import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';

import { ConfigRouteComponent } from './config-route.component';

describe('ConfigRouteComponent', () => {
  let component: ConfigRouteComponent;
  let fixture: ComponentFixture<ConfigRouteComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ConfigRouteComponent],
      imports: [
        HttpClientTestingModule,
        MatAutocompleteModule,
        BrowserAnimationsModule
      ],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            params: of({ id: '1' }),
            snapshot: { params: { id: '1' } }
          }
        }
      ],
      schemas: [NO_ERRORS_SCHEMA]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ConfigRouteComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
