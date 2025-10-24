import { Component, Input, Output, EventEmitter, OnInit, OnChanges, SimpleChanges, ViewChild, ElementRef, AfterViewChecked } from '@angular/core';
import { SparqiService } from '../../../core/services/sparqi.service';
import { SparqiMessage, SparqiContext, SparqiSession } from '../../../core/models/sparqi.models';
import { MatDialog } from '@angular/material/dialog';
import { OntologyVisualizationDialogComponent } from './ontology-visualization-dialog/ontology-visualization-dialog.component';

@Component({
  selector: 'app-sparqi-chat',
  templateUrl: './sparqi-chat.component.html',
  styleUrls: ['./sparqi-chat.component.scss']
})
export class SparqiChatComponent implements OnInit, OnChanges, AfterViewChecked {
  @Input() routeId!: string;
  @Input() isOpen: boolean = false;
  @Output() closePanel = new EventEmitter<void>();

  @ViewChild('messagesContainer') private messagesContainer!: ElementRef;

  messages: SparqiMessage[] = [];
  currentMessage: string = '';
  isLoading: boolean = false;
  sessionId: string | null = null;
  context: SparqiContext | null = null;
  error: string | null = null;
  showContext: boolean = false;
  private shouldScrollToBottom = false;
  lastFailedMessage: string = '';

  constructor(
    private sparqiService: SparqiService,
    private dialog: MatDialog
  ) {}

  ngOnInit(): void {
    if (this.routeId) {
      this.initializeSession();
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    // If routeId changes, reinitialize session
    if (changes['routeId'] && !changes['routeId'].firstChange) {
      this.initializeSession();
    }
  }

  ngAfterViewChecked(): void {
    if (this.shouldScrollToBottom) {
      this.scrollToBottom();
      this.shouldScrollToBottom = false;
    }
    this.addCopyButtonsToCodeBlocks();
  }

  /**
   * Initialize or resume session
   */
  private initializeSession(): void {
    this.isLoading = true;
    this.error = null;
    this.messages = [];

    this.sparqiService.getOrCreateSession(this.routeId).subscribe({
      next: (session: SparqiSession) => {
        this.sessionId = session.sessionId;

        // Load conversation history
        this.loadHistory();

        // If new session, show welcome message
        if (session.welcomeMessage) {
          this.messages.unshift({
            role: 'assistant',
            content: session.welcomeMessage,
            timestamp: session.createdAt
          });
          this.shouldScrollToBottom = true;
        }

        this.isLoading = false;
      },
      error: (error) => {
        this.error = error.message || 'Failed to initialize chat session';
        this.isLoading = false;
        console.error('Session initialization error:', error);
      }
    });
  }

  /**
   * Load conversation history
   */
  private loadHistory(): void {
    if (!this.sessionId) return;

    this.sparqiService.getSessionHistory(this.sessionId).subscribe({
      next: (history: SparqiMessage[]) => {
        this.messages = history;
        this.shouldScrollToBottom = true;
      },
      error: (error) => {
        console.error('Failed to load history:', error);
        // Don't show error to user, history load failure is not critical
      }
    });
  }

  /**
   * Send message to SPARQi
   */
  sendMessage(): void {
    if (!this.currentMessage.trim() || this.isLoading || !this.sessionId) {
      return;
    }

    const userMessage = this.currentMessage.trim();
    this.currentMessage = '';
    this.error = null;
    this.lastFailedMessage = '';

    // Add user message to UI immediately
    const userMsg: SparqiMessage = {
      role: 'user',
      content: userMessage,
      timestamp: new Date()
    };
    this.messages.push(userMsg);
    this.shouldScrollToBottom = true;

    // Send to backend
    this.isLoading = true;
    this.sparqiService.sendMessage(this.sessionId, userMessage).subscribe({
      next: (response: SparqiMessage) => {
        // Add assistant response
        this.messages.push(response);
        this.shouldScrollToBottom = true;
        this.isLoading = false;
      },
      error: (error) => {
        this.error = error.message || 'Failed to send message';
        this.lastFailedMessage = userMessage;
        this.isLoading = false;
        console.error('Send message error:', error);
      }
    });
  }

  /**
   * Retry last failed message
   */
  retryLastMessage(): void {
    if (this.lastFailedMessage) {
      this.currentMessage = this.lastFailedMessage;
      this.error = null;
      this.sendMessage();
    }
  }

  /**
   * Load and display route context
   */
  loadContext(): void {
    if (!this.sessionId) return;

    this.showContext = !this.showContext;

    if (this.showContext && !this.context) {
      this.sparqiService.getSessionContext(this.sessionId).subscribe({
        next: (context: SparqiContext) => {
          this.context = context;
        },
        error: (error) => {
          console.error('Failed to load context:', error);
          this.showContext = false;
        }
      });
    }
  }

  /**
   * Clear chat and create new session
   */
  clearChat(): void {
    if (confirm('Are you sure you want to clear this conversation? This cannot be undone.')) {
      this.isLoading = true;
      this.error = null;

      this.sparqiService.clearSession(this.routeId).subscribe({
        next: () => {
          this.messages = [];
          this.context = null;
          this.showContext = false;
          this.initializeSession();
        },
        error: (error) => {
          this.error = 'Failed to clear session';
          this.isLoading = false;
          console.error('Clear session error:', error);
        }
      });
    }
  }

  /**
   * Close the chat panel
   */
  close(): void {
    this.closePanel.emit();
  }

  /**
   * Scroll messages to bottom
   */
  private scrollToBottom(): void {
    try {
      if (this.messagesContainer) {
        const element = this.messagesContainer.nativeElement;
        element.scrollTop = element.scrollHeight;
      }
    } catch(err) {
      console.error('Scroll error:', err);
    }
  }

  /**
   * Handle enter key in textarea
   */
  onKeyDown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && event.ctrlKey) {
      event.preventDefault();
      this.sendMessage();
    }
  }

  /**
   * Open ontology visualization dialog
   * Opens even if element count is zero to allow manual cache refresh
   */
  openOntologyVisualization(): void {
    if (!this.context) {
      return;
    }

    this.dialog.open(OntologyVisualizationDialogComponent, {
      data: {
        routeId: this.routeId,
        elementCount: this.context.ontologyElementCount || 0
      },
      maxWidth: '90vw',
      maxHeight: '90vh',
      panelClass: 'ontology-dialog-container'
    });
  }

  /**
   * Add copy buttons to code blocks in markdown
   */
  private addCopyButtonsToCodeBlocks(): void {
    const codeBlocks = document.querySelectorAll('.markdown-content pre:not(.copy-button-added)');

    codeBlocks.forEach((block: Element) => {
      // Mark as processed
      block.classList.add('copy-button-added');

      // Create wrapper if not already wrapped
      if (!block.parentElement?.classList.contains('code-block-wrapper')) {
        const wrapper = document.createElement('div');
        wrapper.className = 'code-block-wrapper';
        block.parentNode?.insertBefore(wrapper, block);
        wrapper.appendChild(block);

        // Create copy button
        const copyButton = document.createElement('button');
        copyButton.className = 'code-copy-btn';
        copyButton.innerHTML = '<mat-icon class="mat-icon material-icons">content_copy</mat-icon>';
        copyButton.title = 'Copy code';

        // Add click handler
        copyButton.addEventListener('click', () => {
          const code = block.querySelector('code');
          if (code) {
            navigator.clipboard.writeText(code.textContent || '').then(() => {
              copyButton.classList.add('copied');
              setTimeout(() => copyButton.classList.remove('copied'), 2000);
            }).catch(err => {
              console.error('Failed to copy code:', err);
            });
          }
        });

        wrapper.appendChild(copyButton);
      }
    });
  }
}
