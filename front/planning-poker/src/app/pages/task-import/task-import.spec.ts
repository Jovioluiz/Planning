import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TaskImport } from './task-import';

describe('TaskImport', () => {
  let component: TaskImport;
  let fixture: ComponentFixture<TaskImport>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TaskImport]
    })
    .compileComponents();

    fixture = TestBed.createComponent(TaskImport);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
