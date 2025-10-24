import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ConfigRouteComponent } from './config-route.component';

describe('ConfigRouteComponent', () => {
  let component: ConfigRouteComponent;
  let fixture: ComponentFixture<ConfigRouteComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
    declarations: [ConfigRouteComponent]
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
