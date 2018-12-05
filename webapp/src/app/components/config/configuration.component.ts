import {ChangeDetectionStrategy, Component, OnInit} from '@angular/core';
import {BoardsService} from '../../services/boards.service';
import {AppHeaderService} from '../../services/app-header.service';
import {BehaviorSubject, Observable, Subject} from 'rxjs';
import {List} from 'immutable';
import {FormControl, FormGroup, Validators} from '@angular/forms';
import {map, take} from 'rxjs/operators';
import {IssueQlUtil} from '../../common/parsers/issue-ql/issue-ql.util';
import * as issueQlParser from '../../common/parsers/issue-ql/pegjs/issue-ql.generated';
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
  private _selected = -1;
  private _selectedTemplate = false;
  selectedBoardOrTemplate$: Observable<any>;
  editError: string;

  // For creating boards
  createForm: FormGroup;
  createError: string;

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
    this.createForm = new FormGroup({
      createJson: new FormControl('', Validators.required),
      template: new FormControl('')
    });
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

  onOpenForEdit(template: boolean, boardOrTemplate: any) {
    const id = boardOrTemplate['id'];
    this._selected = id;
    this._selectedTemplate = template;

    // TODO progress and errors
    this.selectedBoardOrTemplate$ = this._boardsService.loadBoardOrTemplateConfigJson(template, id);
  }

  onCloseForEdit(template: boolean, boardOrTemplate: any) {
    const id = boardOrTemplate['id'];
    if (this._selected === id && template !== this._selectedTemplate) {
      this.editError = null;
    }
  }

  isSelected(template: boolean, boardOrTemplate: any) {
    const selected = boardOrTemplate['id'] === this._selected && template === this._selectedTemplate;
    return selected;
  }

  clearSaveJsonErrors() {
    this.createError = null;
  }

  onSaveCreatedBoardOrTemplate() {
    console.log('Saving created board or template');
    const json: string  = this.createForm.controls['createJson'].value;

    const jsonObject: Object = this.checkJson(json);
    if (!jsonObject) {
      this.createError = 'Contents must be valid json';
      return;
    }
    const issueQlError = this.checkManualSwimlanesIssueQl(jsonObject);
    if (issueQlError) {
      this.createError = issueQlError;
      return;
    }

    if (!this.checkDemoAndLogMessage()) {
      return;
    }

    const template: boolean = this.createForm.controls['template'].value;

    // TODO progress and errors
    this._boardsService.createBoardOrTemplate(template, json)
      .pipe(
        map<any, ConfigBoardsView>(data => {
          return this.toConfigBoardView(data);
        }),
        take(1)
      )
      .subscribe(
        value => {
          this.config$.next(value);
          this.createForm.controls['createJson'].setValue('');
          });

    this.config$
      .pipe(
        take(1)
      )
      .subscribe(data => {
      });
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

  private checkManualSwimlanesIssueQl(boardConfig: Object): string {
    const cfg: any = boardConfig['config'];
    if (!cfg) {
      // Proper validation happens on server
      return;
    }
    const mslConfig = cfg['manual-swimlanes'];
    if (mslConfig) {
      if (!Array.isArray(mslConfig)) {
        // Proper validation happens on server
        return null;
      }
      for (const msl of mslConfig) {
        const entries: any = msl['entries'];
        if (!Array.isArray(entries)) {
          // Proper validation happens on server
          return null;
        }
        for (const entry of entries) {
          let iql = entry['issue-ql'];
          if (!iql) {
            // Proper validation happens on server
            return null;
          }
          iql = iql.trim();
          let error: issueQlParser.SyntaxError;
          if (iql.length > 0) {
            error = IssueQlUtil.validateIssueQl(iql);
            if (error) {
              return `"Invalid Issue QL: "${iql}". The parser error is: ${error}"`;
            }
          }
        }
      }
    }
  }

  private checkDemoAndLogMessage(): boolean {
    if (environment.demo) {
      this._progressLog.startUserAction().logWarning('This is a read-only demo instance. The selected functionality is not available');
      return false;
    }
    return true;
  }


  onConfigEvent(event: BoardConfigEvent) {
    if (event.type === BoardConfigType.CLEAR_JSON_ERROR) {
      this.editError = null;
    } else if (event.type === BoardConfigType.SAVE) {

      console.log('Saving edited board');
      const boardJson: any = event.payload;
      const jsonObject: Object = this.checkJson(boardJson);
      if (!jsonObject) {
        this.createError = 'Contents must be valid json';
        return;
      }
      const issueQlError = this.checkManualSwimlanesIssueQl(jsonObject);
      if (issueQlError) {
        this.createError = issueQlError;
        return;
      }

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
      this._boardsService.deleteBoardOrTemplate(event.templateId, event.boardId)
        .pipe(
          map(data => this.toConfigBoardView(data)),
          take(1)
        )
        .subscribe(
          value => {
            this.config$.next(value);
            this._selected = -1;
          }
        );
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

