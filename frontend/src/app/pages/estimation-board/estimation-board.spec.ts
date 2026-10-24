import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EstimationBoard } from './estimation-board';

describe('EstimationBoard', () => {
  let component: EstimationBoard;
  let fixture: ComponentFixture<EstimationBoard>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EstimationBoard]
    })
    .compileComponents();

    fixture = TestBed.createComponent(EstimationBoard);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
