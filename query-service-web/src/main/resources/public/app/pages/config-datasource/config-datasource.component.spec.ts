import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { NO_ERRORS_SCHEMA } from '@angular/core';

import { ConfigDatasourceComponent } from './config-datasource.component';

describe('ConfigDatasourceComponent', () => {
  let component: ConfigDatasourceComponent;
  let fixture: ComponentFixture<ConfigDatasourceComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ConfigDatasourceComponent],
      imports: [HttpClientTestingModule],
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

    fixture = TestBed.createComponent(ConfigDatasourceComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
