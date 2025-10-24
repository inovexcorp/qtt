import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ConfigDatasourceModalComponent } from './config-datasource-modal.component';

describe('ConfigDatasourceModalComponent', () => {
  let component: ConfigDatasourceModalComponent;
  let fixture: ComponentFixture<ConfigDatasourceModalComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
    declarations: [ConfigDatasourceModalComponent]
})
    .compileComponents();

    fixture = TestBed.createComponent(ConfigDatasourceModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
