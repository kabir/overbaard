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
import {BoardConfigCheckerUtil} from './board-config-checker.util';

@Component({
  selector: 'app-board-configuration-input',
  templateUrl: './board-config-input.component.html',
  styleUrls: ['./board-config-input.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BoardConfigInputComponent implements OnInit, OnChanges {

  // This will only be set if editing a board, for new boards it will be null
  @Input()
  config: any;

  isNew = false;

  @Input()
  canEdit: boolean;

  @Input()
  boardName: string;

  @Input()
  templateId: number;

  @Input()
  boardId: number;

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
    this.isNew = !this.config;
  }

  ngOnChanges(changes: SimpleChanges) {
    const configJsonChange: SimpleChange  = changes['config'];
    if (configJsonChange && !configJsonChange.firstChange && configJsonChange.currentValue && !configJsonChange.previousValue) {
      this.editForm.controls['editJson'].setValue(this.formatJson(this.config));
      console.log('change');
      console.log(this.config);

      this.isNew = !this.config;
    }
  }

  private formatJson(config: any): string {
    if (!config) {
      return '';
    }
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
    const json: string = this.editForm.value.editJson;
    this.jsonError = new BoardConfigCheckerUtil(json).check();
    if (this.jsonError) {
      this.jsonError = 'Contents must be valid json';
      return;
    }
    const type: BoardConfigType = this.isNew ? BoardConfigType.NEW : BoardConfigType.SAVE;
    this.configEvent.emit(new BoardConfigEvent(type, this.templateId, this.boardId, this.editForm.value.editJson));
  }

  clearJsonErrors() {
    this.jsonError = null;
  }

  onDeleteConfig() {
    this.configEvent.emit(new BoardConfigEvent(BoardConfigType.DELETE, this.templateId, this.boardId, null));
  }

  private checkJson(value: string): Object {
    try {
      return JSON.parse(value);
    } catch (e) {
      return null;
    }
  }
}
