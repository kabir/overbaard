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
import {FormControl, FormGroup, Validators} from '@angular/forms';

@Component({
  selector: 'app-board-configuration',
  templateUrl: './board-configuration.component.html',
  styleUrls: ['./board-configuration.component.scss'],
  providers: [BoardsService],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BoardConfigurationComponent implements OnInit, OnChanges {
  private _configJson: string;

  templateBoards: any[];

  @Input()
  canEdit: boolean;

  @Input()
  boardName: string;

  @Input()
  template: boolean;

  @Input()
  jsonError: string;

  @Output()
  deleteBoard: EventEmitter<null> = new EventEmitter<null>();

  @Output()
  saveBoard: EventEmitter<string> = new EventEmitter<string>();

  @Output()
  clearJsonError: EventEmitter<null> = new EventEmitter<null>();

  deleting = false;
  deleteForm: FormGroup;
  editForm: FormGroup;

  constructor() {
  }

  @Input()
  set configJson(json: string) {
    if (this.template) {
      if (json) {
        const jsonObject: any = JSON.parse(json);
        if (jsonObject['boards']) {
          this.templateBoards = jsonObject['boards'];
          delete jsonObject['boards'];
          json = JSON.stringify(jsonObject, null, 2);
        }
      }
    }
    this._configJson = json;
  }

  ngOnInit() {
    this.editForm = new FormGroup({
      editJson: new FormControl(this._configJson, Validators.required)
    });
  }

  ngOnChanges(changes: SimpleChanges) {
    const configJsonChange: SimpleChange  = changes['configJson'];
    if (configJsonChange && configJsonChange.currentValue && !configJsonChange.previousValue) {
      this.editForm.controls['editJson'].setValue(this._configJson);
    }
  }


  onToggleDelete(event: Event) {
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

