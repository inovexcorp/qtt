import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TextFieldModule } from '@angular/cdk/text-field';
import { of, throwError } from 'rxjs';
import { NO_ERRORS_SCHEMA, SimpleChange } from '@angular/core';
import { SparqiChatComponent } from './sparqi-chat.component';
import { SparqiService } from '../../../core/services/sparqi.service';
import { SparqiSession, SparqiMessage, SparqiContext } from '../../../core/models/sparqi.models';

describe('SparqiChatComponent', () => {
  let component: SparqiChatComponent;
  let fixture: ComponentFixture<SparqiChatComponent>;
  let mockSparqiService: jasmine.SpyObj<SparqiService>;
  let mockDialog: jasmine.SpyObj<MatDialog>;

  beforeEach(async () => {
    mockSparqiService = jasmine.createSpyObj('SparqiService', [
      'getOrCreateSession',
      'getSessionHistory',
      'sendMessage',
      'getSessionContext',
      'clearSession'
    ]);
    mockDialog = jasmine.createSpyObj('MatDialog', ['open']);

    await TestBed.configureTestingModule({
      declarations: [SparqiChatComponent],
      imports: [HttpClientTestingModule, TextFieldModule],
      providers: [
        { provide: SparqiService, useValue: mockSparqiService },
        { provide: MatDialog, useValue: mockDialog }
      ],
      schemas: [NO_ERRORS_SCHEMA]
    }).compileComponents();

    fixture = TestBed.createComponent(SparqiChatComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('ngOnInit', () => {
    it('should initialize session when routeId is provided', () => {
      const mockSession: SparqiSession = {
        sessionId: 'session-123',
        userId: '1',
        routeId: 'test-route',
        createdAt: new Date()
      };
      const mockHistory: SparqiMessage[] = [];

      component.routeId = 'test-route';
      mockSparqiService.getOrCreateSession.and.returnValue(of(mockSession));
      mockSparqiService.getSessionHistory.and.returnValue(of(mockHistory));

      component.ngOnInit();

      expect(mockSparqiService.getOrCreateSession).toHaveBeenCalledWith('test-route');
      expect(component.sessionId).toBe('session-123');
    });

    it('should display welcome message for new session', () => {
      const mockSession: SparqiSession = {
        sessionId: 'session-123',
        userId: '1',
        routeId: 'test-route',
        createdAt: new Date(),
        welcomeMessage: 'Welcome to SPARQi!'
      };
      const mockHistory: SparqiMessage[] = [];

      component.routeId = 'test-route';
      mockSparqiService.getOrCreateSession.and.returnValue(of(mockSession));
      mockSparqiService.getSessionHistory.and.returnValue(of(mockHistory));

      component.ngOnInit();

      expect(component.messages.length).toBe(1);
      expect(component.messages[0].content).toBe('Welcome to SPARQi!');
      expect(component.messages[0].role).toBe('assistant');
    });

    it('should handle session initialization error', () => {
      component.routeId = 'test-route';
      mockSparqiService.getOrCreateSession.and.returnValue(
        throwError(() => new Error('Failed to initialize'))
      );

      spyOn(console, 'error');

      component.ngOnInit();

      expect(component.error).toContain('Failed to initialize');
      expect(component.isLoading).toBe(false);
      expect(console.error).toHaveBeenCalled();
    });
  });

  describe('ngOnChanges', () => {
    it('should reinitialize session when routeId changes', () => {
      const mockSession: SparqiSession = {
        sessionId: 'new-session',
        userId: '1',
        routeId: 'new-route',
        createdAt: new Date()
      };
      const mockHistory: SparqiMessage[] = [];

      mockSparqiService.getOrCreateSession.and.returnValue(of(mockSession));
      mockSparqiService.getSessionHistory.and.returnValue(of(mockHistory));

      // Simulate Angular's behavior: update the property before calling ngOnChanges
      component.routeId = 'new-route';
      component.ngOnChanges({
        routeId: new SimpleChange('old-route', 'new-route', false)
      });

      expect(mockSparqiService.getOrCreateSession).toHaveBeenCalledWith('new-route');
    });

    it('should not reinitialize on first change', () => {
      component.ngOnChanges({
        routeId: new SimpleChange(null, 'test-route', true)
      });

      expect(mockSparqiService.getOrCreateSession).not.toHaveBeenCalled();
    });
  });

  describe('sendMessage', () => {
    beforeEach(() => {
      component.sessionId = 'session-123';
      component.routeId = 'test-route';
    });

    it('should send message and display response', () => {
      const userMessage = 'What is SPARQL?';
      const mockResponse: SparqiMessage = {
        role: 'assistant',
        content: 'SPARQL is a query language...',
        timestamp: new Date()
      };

      component.currentMessage = userMessage;
      mockSparqiService.sendMessage.and.returnValue(of(mockResponse));

      component.sendMessage();

      expect(component.messages.length).toBe(2);
      expect(component.messages[0].role).toBe('user');
      expect(component.messages[0].content).toBe(userMessage);
      expect(component.messages[1].role).toBe('assistant');
      expect(component.currentMessage).toBe('');
      expect(component.isLoading).toBe(false);
    });

    it('should not send empty message', () => {
      component.currentMessage = '   ';

      component.sendMessage();

      expect(mockSparqiService.sendMessage).not.toHaveBeenCalled();
    });

    it('should not send message when loading', () => {
      component.currentMessage = 'Test message';
      component.isLoading = true;

      component.sendMessage();

      expect(mockSparqiService.sendMessage).not.toHaveBeenCalled();
    });

    it('should not send message when no session ID', () => {
      component.sessionId = null;
      component.currentMessage = 'Test message';

      component.sendMessage();

      expect(mockSparqiService.sendMessage).not.toHaveBeenCalled();
    });

    it('should handle send message error', () => {
      const userMessage = 'Test message';
      component.currentMessage = userMessage;
      mockSparqiService.sendMessage.and.returnValue(
        throwError(() => new Error('Failed to send'))
      );

      spyOn(console, 'error');

      component.sendMessage();

      expect(component.error).toContain('Failed to send');
      expect(component.lastFailedMessage).toBe(userMessage);
      expect(component.isLoading).toBe(false);
      expect(console.error).toHaveBeenCalled();
    });
  });

  describe('retryLastMessage', () => {
    it('should retry last failed message', () => {
      component.lastFailedMessage = 'Failed message';
      component.sessionId = 'session-123';
      component.routeId = 'test-route';
      const mockResponse: SparqiMessage = {
        role: 'assistant',
        content: 'Response',
        timestamp: new Date()
      };
      mockSparqiService.sendMessage.and.returnValue(of(mockResponse));

      component.retryLastMessage();

      // currentMessage gets cleared after sending, so check that sendMessage was called
      expect(mockSparqiService.sendMessage).toHaveBeenCalledWith('session-123', 'Failed message');
      expect(component.currentMessage).toBe('');
      expect(component.error).toBeNull();
    });

    it('should not retry if no failed message', () => {
      component.lastFailedMessage = '';

      component.retryLastMessage();

      expect(mockSparqiService.sendMessage).not.toHaveBeenCalled();
    });
  });

  describe('loadContext', () => {
    beforeEach(() => {
      component.sessionId = 'session-123';
    });

    it('should toggle context visibility', () => {
      component.showContext = false;
      // Set context so it won't try to load from service
      component.context = {
        routeId: 'test-route',
        currentTemplate: 'SELECT * WHERE { ?s ?p ?o }',
        routeDescription: 'Test Route Description',
        graphMartUri: 'http://example.org/graphmart',
        layerUris: ['http://example.org/layer1'],
        datasourceUrl: 'http://example.com/sparql',
        ontologyElementCount: 100
      };

      component.loadContext();

      expect(component.showContext).toBe(true);
    });

    it('should load context when not already loaded', () => {
      const mockContext: SparqiContext = {
        routeId: 'test-route',
        currentTemplate: 'SELECT * WHERE { ?s ?p ?o }',
        routeDescription: 'Test Route Description',
        graphMartUri: 'http://example.org/graphmart',
        layerUris: ['http://example.org/layer1'],
        datasourceUrl: 'http://example.com/sparql',
        ontologyElementCount: 100
      };
      component.showContext = false;
      mockSparqiService.getSessionContext.and.returnValue(of(mockContext));

      component.loadContext();

      expect(mockSparqiService.getSessionContext).toHaveBeenCalledWith('session-123');
      expect(component.context).toEqual(mockContext);
    });

    it('should not reload context if already loaded', () => {
      component.context = {
        routeId: 'test-route',
        currentTemplate: 'SELECT * WHERE { ?s ?p ?o }',
        routeDescription: 'Test Route Description',
        graphMartUri: 'http://example.org/graphmart',
        layerUris: ['http://example.org/layer1'],
        datasourceUrl: 'http://example.com/sparql',
        ontologyElementCount: 100
      };
      component.showContext = false;

      component.loadContext();

      expect(mockSparqiService.getSessionContext).not.toHaveBeenCalled();
      expect(component.showContext).toBe(true);
    });

    it('should handle context load error', () => {
      component.showContext = false;
      mockSparqiService.getSessionContext.and.returnValue(
        throwError(() => new Error('Failed to load'))
      );

      spyOn(console, 'error');

      component.loadContext();

      expect(component.showContext).toBe(false);
      expect(console.error).toHaveBeenCalled();
    });
  });

  describe('clearChat', () => {
    beforeEach(() => {
      component.sessionId = 'session-123';
      component.routeId = 'test-route';
      spyOn(window, 'confirm').and.returnValue(true);
    });

    it('should clear chat and create new session', () => {
      const mockSession: SparqiSession = {
        sessionId: 'new-session',
        userId: '1',
        routeId: 'test-route',
        createdAt: new Date()
      };
      const mockHistory: SparqiMessage[] = [];

      mockSparqiService.clearSession.and.returnValue(of(undefined));
      mockSparqiService.getOrCreateSession.and.returnValue(of(mockSession));
      mockSparqiService.getSessionHistory.and.returnValue(of(mockHistory));

      component.messages = [
        { role: 'user', content: 'Test', timestamp: new Date() }
      ];

      component.clearChat();

      expect(mockSparqiService.clearSession).toHaveBeenCalledWith('test-route');
      expect(component.messages.length).toBe(0);
      expect(component.context).toBeNull();
      expect(component.showContext).toBe(false);
    });

    it('should not clear if user cancels', () => {
      (window.confirm as jasmine.Spy).and.returnValue(false);

      component.clearChat();

      expect(mockSparqiService.clearSession).not.toHaveBeenCalled();
    });

    it('should handle clear session error', () => {
      mockSparqiService.clearSession.and.returnValue(
        throwError(() => new Error('Failed to clear'))
      );

      spyOn(console, 'error');

      component.clearChat();

      expect(component.error).toBe('Failed to clear session');
      expect(component.isLoading).toBe(false);
      expect(console.error).toHaveBeenCalled();
    });
  });

  describe('close', () => {
    it('should emit closePanel event', () => {
      spyOn(component.closePanel, 'emit');

      component.close();

      expect(component.closePanel.emit).toHaveBeenCalled();
    });
  });

  describe('onKeyDown', () => {
    it('should send message on Ctrl+Enter', () => {
      component.sessionId = 'session-123';
      component.currentMessage = 'Test message';
      const mockEvent = new KeyboardEvent('keydown', { key: 'Enter', ctrlKey: true });
      spyOn(mockEvent, 'preventDefault');
      const mockResponse: SparqiMessage = {
        role: 'assistant',
        content: 'Response',
        timestamp: new Date()
      };
      mockSparqiService.sendMessage.and.returnValue(of(mockResponse));

      component.onKeyDown(mockEvent);

      expect(mockEvent.preventDefault).toHaveBeenCalled();
      expect(mockSparqiService.sendMessage).toHaveBeenCalled();
    });

    it('should not send message on Enter without Ctrl', () => {
      const mockEvent = new KeyboardEvent('keydown', { key: 'Enter', ctrlKey: false });

      component.onKeyDown(mockEvent);

      expect(mockSparqiService.sendMessage).not.toHaveBeenCalled();
    });
  });

  describe('openOntologyVisualization', () => {
    it('should open ontology visualization dialog', () => {
      component.context = {
        routeId: 'test-route',
        currentTemplate: 'SELECT * WHERE { ?s ?p ?o }',
        routeDescription: 'Test Route Description',
        graphMartUri: 'http://example.org/graphmart',
        layerUris: ['http://example.org/layer1'],
        datasourceUrl: 'http://example.com/sparql',
        ontologyElementCount: 100
      };
      component.routeId = 'test-route';

      component.openOntologyVisualization();

      expect(mockDialog.open).toHaveBeenCalled();
    });

    it('should not open dialog if no context', () => {
      component.context = null;

      component.openOntologyVisualization();

      expect(mockDialog.open).not.toHaveBeenCalled();
    });
  });
});
