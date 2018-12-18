import {ChangeDetectionStrategy, Component, OnInit} from '@angular/core';
import {BoardsService} from '../../services/boards.service';
import {AppHeaderService} from '../../services/app-header.service';
import {BehaviorSubject, Observable, Subject} from 'rxjs';
import {List} from 'immutable';
import {FormControl, FormGroup, Validators} from '@angular/forms';
import {map, take} from 'rxjs/operators';
import {UrlService} from '../../services/url.service';
import {environment} from '../../../environments/environment';
import {ProgressLogService} from '../../services/progress-log.service';
import {BoardConfigEvent, BoardConfigType} from './board-config.event';

@Component({
  selector: 'app-configuration',
  templateUrl: './configuration.component.html',
  styleUrls: ['./configuration.component.scss'],
  providers: [BoardsService],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ConfigurationComponent implements OnInit {

  // There isn't much going on with data here, so we're not using redux in this area (for now). Instead we push
  // the current data to this subject
  readonly config$: Subject<ConfigBoardsView> = new BehaviorSubject<ConfigBoardsView>(
    {
      boards: List<any>(),
      templates: List<any>(),
      canEditCustomFields: false,
      rankCustomFieldId: 0,
      epicLinkCustomFieldId: 0,
      epicNameCustomFieldId: 0
      });

  // For editing and deleting boards
  private _selected = null;
  private _selectedTemplate = false;
  selectedBoardOrTemplate$: Observable<any>;

  // For saving the rank id
  customFieldsForm: FormGroup;

  fieldsRestApiUrl: string;

  constructor(private _boardsService: BoardsService,
              appHeaderService: AppHeaderService,
              private _urlService: UrlService,
              private _progressLog: ProgressLogService) {
    appHeaderService.setTitle('Configuration');
  }

  ngOnInit() {
    this.loadBoards();
    this.fieldsRestApiUrl = this._urlService.caclulateRestUrl('rest/api/2/field');
  }

  private loadBoards() {
    // TODO log progress and errors
    this._boardsService.loadBoardsList(false)
      .pipe(
        map(data => this.toConfigBoardView(data)),
        take(1),
      )
      .subscribe(
        value => {
          this.customFieldsForm = new FormGroup({
            rankCustomFieldId: new FormControl(value.rankCustomFieldId, Validators.pattern('[0-9]*')),
            epicLinkCustomFieldId: new FormControl(value.epicLinkCustomFieldId, Validators.pattern('[0-9]*')),
            epicNameCustomFieldId: new FormControl(value.epicNameCustomFieldId, Validators.pattern('[0-9]*'))
          });
          this.config$.next(value);
        });
  }

  onOpenForEdit(template: boolean, boardOrTemplate?: any) {
    const id: number = boardOrTemplate ? boardOrTemplate['id'] : -1;
    this._selected = id;
    this._selectedTemplate = template;

    if (boardOrTemplate) {
      // Only load if editing a board
      this.selectedBoardOrTemplate$ = this._boardsService.loadBoardOrTemplateConfigJson(template, id);
    }
  }

  onCloseForEdit(template: boolean, boardOrTemplate?: any) {
    const id: number = boardOrTemplate ? boardOrTemplate['id'] : -1;
  }

  isSelected(template: boolean, boardOrTemplate?: any) {
    const id: number = boardOrTemplate ? boardOrTemplate['id'] : -1;
    return id === this._selected && template === this._selectedTemplate;
  }

  onSaveCustomFieldId() {
    if (!this.checkDemoAndLogMessage()) {
      return;
    }

    this._boardsService.saveCustomFieldsIds(
      this.customFieldsForm.value['rankCustomFieldId'],
      this.customFieldsForm.value['epicLinkCustomFieldId'],
      this.customFieldsForm.value['epicNameCustomFieldId'])
      .pipe(
        take(1)
      )
      .subscribe(
        data => {
          // We need to subscribe here since http actions are cold observables
        }
      );
  }


  private checkJson(value: string): Object {
    try {
      return JSON.parse(value);
    } catch (e) {
      return null;
    }
  }

  private toConfigBoardView(data: any): ConfigBoardsView {
    const boards: List<any> =
      (<any[]>data['boards'])
        .reduce((li, boardCfg) => li.push(boardCfg), List<any>());
    const templates: List<any> =
      (<any[]>data['templates'])
        .reduce((li, boardCfgTemplate) => li.push(boardCfgTemplate), List<any>());
    return {
      boards: boards,
      templates: templates,
      canEditCustomFields: data['can-edit-custom-fields'],
      rankCustomFieldId: data['rank-custom-field-id'],
      epicLinkCustomFieldId: data['epic-link-custom-field-id'],
      epicNameCustomFieldId: data['epic-name-custom-field-id'],
    };
  }

  private checkDemoAndLogMessage(): boolean {
    if (environment.demo) {
      this._progressLog.startUserAction().logWarning('This is a read-only demo instance. The selected functionality is not available');
      return false;
    }
    return true;
  }


  onConfigEvent(event: BoardConfigEvent) {
    if (event.type === BoardConfigType.SAVE) {

      console.log('Saving edited board');
      const boardJson: any = event.payload;

      if (!this.checkDemoAndLogMessage()) {
        return;
      }
      this._boardsService.saveBoardOrTemplate(event.templateId, event.boardId, boardJson)
        .pipe(
          map<any, ConfigBoardsView>(data => this.toConfigBoardView(data)),
          take(1)
        )
        .subscribe(
          value => {
            this.config$.next(value);
          });
    } else if (event.type === BoardConfigType.DELETE) {
      console.log('Deleting board');

      if (!this.checkDemoAndLogMessage()) {
        return;
      }
      this._boardsService.deleteBoardOrTemplate(event.templateId, event.boardId)
        .pipe(
          map(data => this.toConfigBoardView(data)),
          take(1)
        )
        .subscribe(
          value => {
            this.config$.next(value);
            this._selected = null;
          }
        );
    } else if (event.type === BoardConfigType.NEW) {
      console.log('Creating board');
      const boardJson: any = event.payload;

      if (!this.checkDemoAndLogMessage()) {
        return;
      }
      this._boardsService.createBoardOrTemplate(event.templateId, event.boardId, boardJson)
        .pipe(
          map<any, ConfigBoardsView>(data => {
            return this.toConfigBoardView(data);
          }),
          take(1)
        )
        .subscribe(
          value => {
            this.config$.next(value);
            // TODO - find a way to clear the form once saved
          });

      this.config$
        .pipe(
          take(1)
        )
        .subscribe(data => {
        });
    }
  }
}

interface ConfigBoardsView {
  boards: List<any>;
  templates: List<any>;
  canEditCustomFields: boolean;
  rankCustomFieldId: number;
  epicLinkCustomFieldId: number;
  epicNameCustomFieldId: number;
}

