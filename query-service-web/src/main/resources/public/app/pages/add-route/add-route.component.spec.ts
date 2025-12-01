import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';

import { AddRouteComponent } from './add-route.component';

describe('AddRouteComponent', () => {
  let component: AddRouteComponent;
  let fixture: ComponentFixture<AddRouteComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [AddRouteComponent],
      imports: [
        HttpClientTestingModule,
        MatAutocompleteModule,
        BrowserAnimationsModule
      ],
      schemas: [NO_ERRORS_SCHEMA]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AddRouteComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
