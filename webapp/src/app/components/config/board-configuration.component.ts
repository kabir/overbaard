import {ChangeDetectionStrategy, Component, EventEmitter, Input, Output} from '@angular/core';
import {BoardConfigEvent} from './board-config.event';

@Component({
  selector: 'app-board-configuration',
  templateUrl: './board-configuration.component.html',
  styleUrls: ['./board-configuration.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BoardConfigurationComponent {

  private _configObject: any;

  @Input()
  configId: number;

  @Input()
  template: boolean;

  @Input()
  canEdit: boolean;

  templateBoards: any[];

  name: string;

  templateId: number;

  boardId: number;

  @Output()
  configEvent: EventEmitter<BoardConfigEvent> = new EventEmitter<BoardConfigEvent>();

  selectedTemplateBoard: any;

  constructor() {
  }

  @Input()
  set config(config: any) {
    if (this.template) {
      if (config) {
        if (config['boards']) {
          this.templateBoards = config['boards'];
          if (!this.templateBoards) {
            this.templateBoards = [];
          }
          delete config['boards'];
        }
      }
    }
    if (config) {
      this.name = config['name'];
      if (this.template) {
        this.templateId = this.configId;
      } else {
        this.boardId = this.configId;
      }
    }
    this._configObject = config;
  }

  get config(): any {
    return this._configObject;
  }

  onOpenTemplateBoardForEdit(templateBoardId: any) {
    this.selectedTemplateBoard = templateBoardId;
  }

  onCloseTemplateBoardForEdit(templateBoardId: any) {
    this.selectedTemplateBoard = null;
  }
}

