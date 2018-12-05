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
import {FormControl, FormGroup, Validators} from '@angular/forms';
import {BoardConfigEvent, BoardConfigType} from './board-config.event';

@Component({
  selector: 'app-board-configuration-input',
  templateUrl: './board-config-input.component.html',
  styleUrls: ['./board-config-input.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BoardConfigInputComponent implements OnInit, OnChanges {

  @Input()
  config: any;

  @Input()
  canEdit: boolean;

  @Input()
  boardName: string;

  @Input()
  templateId: number;

  @Input()
  boardId: number;

  @Input()
  jsonError: string;

  @Output()
  configEvent: EventEmitter<BoardConfigEvent> = new EventEmitter<BoardConfigEvent>();

  deleting = false;
  deleteForm: FormGroup;
  editForm: FormGroup;

  ngOnInit() {
    this.editForm = new FormGroup({
      editJson: new FormControl(this.formatJson(this.config), Validators.required)
    });
  }

  ngOnChanges(changes: SimpleChanges) {
    const configJsonChange: SimpleChange  = changes['config'];
    if (configJsonChange && !configJsonChange.firstChange && configJsonChange.currentValue && !configJsonChange.previousValue) {
      this.editForm.controls['editJson'].setValue(this.formatJson(this.config));
    }
  }

  private formatJson(config: any): string {
    return JSON.stringify(config, null, 2);
  }

  onToggleDelete(event: MouseEvent) {
    this.deleting = !this.deleting;
    if (this.deleting) {
      this.deleteForm = new FormGroup({
        boardName: new FormControl('', Validators.compose([Validators.required, (control: FormControl) => {
          if (this.boardName !== control.value) {
            return {'boardName' : true};
          }
          return null;
        }]))
      });
    }
    event.preventDefault();
  }

  onSaveConfig() {
    this.configEvent.emit(new BoardConfigEvent(BoardConfigType.SAVE, this.templateId, this.boardId, this.editForm.value.editJson));
  }

  clearJsonErrors() {
    this.configEvent.emit(new BoardConfigEvent(BoardConfigType.CLEAR_JSON_ERROR, this.templateId, this.boardId, null));
  }

  onDeleteConfig() {
    this.configEvent.emit(new BoardConfigEvent(BoardConfigType.DELETE, this.templateId, this.boardId, null));
  }
}
