import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnInit,
  Output,
  SimpleChange,
  SimpleChanges
} from '@angular/core';
import {BoardsService} from '../../services/boards.service';
import {UntypedFormControl, UntypedFormGroup, Validators} from '@angular/forms';

@Component({
  selector: 'app-board-configuration',
  templateUrl: './board-configuration.component.html',
  styleUrls: ['./board-configuration.component.scss'],
  providers: [BoardsService],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BoardConfigurationComponent implements OnInit, OnChanges {
  @Input()
  configJson: string;

  @Input()
  canEdit: boolean;

  @Input()
  boardName: string;

  @Input()
  jsonError: string;

  @Output()
  deleteBoard: EventEmitter<null> = new EventEmitter<null>();

  @Output()
  saveBoard: EventEmitter<string> = new EventEmitter<string>();

  @Output()
  clearJsonError: EventEmitter<null> = new EventEmitter<null>();

  deleting = false;
  deleteForm: UntypedFormGroup;
  editForm: UntypedFormGroup;

  constructor() {
  }

  ngOnInit() {
    this.editForm = new UntypedFormGroup({
      editJson: new UntypedFormControl(this.configJson, Validators.required)
    });
  }

  ngOnChanges(changes: SimpleChanges) {
    const configJsonChange: SimpleChange  = changes['configJson'];
    if (configJsonChange && configJsonChange.currentValue && !configJsonChange.previousValue) {
      this.editForm.controls['editJson'].setValue(configJsonChange.currentValue);
    }
  }

  onToggleDelete(event: Event) {
    this.deleting = !this.deleting;
    if (this.deleting) {
      this.deleteForm = new UntypedFormGroup({
        boardName: new UntypedFormControl('', Validators.compose([Validators.required, (control: UntypedFormControl) => {
            if (this.boardName !== control.value) {
              return {'boardName' : true};
            }
          return null;
        }]))
      });
    }
    event.preventDefault();
  }

  private formatAsJson(data: any): string {
    return JSON.stringify(data, null, 2);
  }

  clearJsonErrors() {
    this.clearJsonError.emit(null);
  }

  onDeleteBoard() {
    this.deleteBoard.emit(null);
  }

  onSaveBoard() {
    this.saveBoard.emit(this.editForm.value.editJson);
  }
}

