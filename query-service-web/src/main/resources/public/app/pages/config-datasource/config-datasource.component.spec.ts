import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ConfigDatasourceComponent } from './config-datasource.component';

describe('ConfigDatasourceComponent', () => {
  let component: ConfigDatasourceComponent;
  let fixture: ComponentFixture<ConfigDatasourceComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
    declarations: [ConfigDatasourceComponent]
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
