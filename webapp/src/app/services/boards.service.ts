import {Injectable} from '@angular/core';
import {Observable, throwError as _throw} from 'rxjs';
import {UrlService} from './url.service';
import {HttpClient, HttpErrorResponse, HttpHeaders} from '@angular/common/http';
import {Progress, ProgressLogService} from './progress-log.service';
import {catchError, map, tap, timeout} from 'rxjs/operators';

@Injectable()
export class
BoardsService {
  private _timeout = 30000;

  constructor(private _restUrlService: UrlService, private _httpClient: HttpClient, private _progressLog: ProgressLogService) {
  }

  loadBoardsList(summaryOnly: boolean): Observable<any[]> {
    const progress: Progress = this._progressLog.startUserAction();
    const path: string = this._restUrlService.caclulateRestUrl(
      summaryOnly ? UrlService.OVERBAARD_REST_PREFIX + '/boards' : UrlService.OVERBAARD_REST_PREFIX + '/boards?full=true');
    return this.executeRequest(progress, this._httpClient.get(path))
      .pipe(
        map(r => summaryOnly ? r['boards'] : r)
      );
  }

  loadBoardOrTemplateConfigJson(template: boolean, id: number): Observable<any> {
    const progress: Progress = this._progressLog.startUserAction();
    const restUrl = template ? '/templates/' : '/boards/';
    const path: string = this._restUrlService.caclulateRestUrl(UrlService.OVERBAARD_REST_PREFIX + restUrl + id);
    return this.executeRequest(progress, this._httpClient.get(path));
  }

  createBoardOrTemplate(templateId: number, boardId: number, json: string): Observable<Object> {
    const progress: Progress = this._progressLog.startUserAction();
    let restUrl = '';
    if (!isNaN(templateId) && templateId >= 0 && boardId === -1) {
      // Save a new board for a template
      restUrl = '/templates/' + templateId + '/boards';
    } else if (isNaN(templateId) && boardId === -1) {
      // Save a new board
      restUrl = '/boards';
    } else if (isNaN(boardId) && templateId === -1) {
      // Save a new template
      restUrl = '/templates';
    } else {
      throw new Error(`The combination of boardId: ${boardId} and templateId: ${templateId} is not known`);
    }
    const path: string = this._restUrlService.caclulateRestUrl(UrlService.OVERBAARD_REST_PREFIX + restUrl);
    console.log('Creating config ' + path);

    return this.executeRequest(
      progress,
      this._httpClient.post(path, json, {
        headers : this.createHeaders()
      }));
  }

  saveBoardOrTemplate(templateId: number, boardId: number, json: string): Observable<Object> {
    const progress: Progress = this._progressLog.startUserAction();
    const template: boolean = !isNaN(templateId);
    const board: boolean = !isNaN(boardId);
    let restUrl = template ? '/templates/' : '/boards/';
    if (template && !board) {
      restUrl = '/templates/' + templateId;
    } else if (!template && board) {
      restUrl = '/boards/' + boardId;
    } else if (template && board) {
      restUrl = '/templates/' + templateId + '/boards/' + boardId;
    }

    const path: string = this._restUrlService.caclulateRestUrl(UrlService.OVERBAARD_REST_PREFIX + restUrl);
    console.log('Saving config ' + path);

    return this.executeRequest(
      progress,
      this._httpClient
        .put(path, json, {
          headers : this.createHeaders()
        }));
  }

  deleteBoardOrTemplate(templateId: number, boardId: number): Observable<Object> {
    const progress: Progress = this._progressLog.startUserAction();
    const template: boolean = !isNaN(templateId);
    const board: boolean = !isNaN(boardId);

    console.log(`t: ${templateId} b: ${boardId} t? ${template} t? ${board}`);

    // If both board and template are passed in, we are deleting a board
    const restUrl: string = !board ? '/templates/' + templateId : '/boards/' + boardId;
    const path = this._restUrlService.caclulateRestUrl(UrlService.OVERBAARD_REST_PREFIX + restUrl);
    console.log('Deleting config ' + path);

    return this.executeRequest(
      progress,
      this._httpClient
      .delete(path, {
        headers: this.createHeaders()
      }));
  }

  private calculateBoardOrTemplateRestPath(templateId: number, boardId: number): string {
    const template: boolean = !isNaN(templateId);
    const board: boolean = !isNaN(boardId);
    let restUrl = template ? '/templates/' : '/boards/';
    if (template && !board) {
      restUrl = '/templates/' + templateId;
    } else if (!template && board) {
      restUrl = '/boards/' + boardId;
    } else if (template && board) {
      restUrl = '/templates/' + templateId + '/boards/' + boardId;
    }
    return this._restUrlService.caclulateRestUrl(UrlService.OVERBAARD_REST_PREFIX + restUrl);
  }

  saveCustomFieldsIds(rank: number, epicLink: number, epicName: number): Observable<Object> {
    const progress: Progress = this._progressLog.startUserAction();

    const path: string = this._restUrlService.caclulateRestUrl(UrlService.OVERBAARD_REST_PREFIX + '/customFieldIds');
    console.log('Saving custom field id ' + path);
    const payload: string = JSON.stringify({
      'rank-custom-field-id': rank,
      'epic-link-custom-field-id': epicLink,
      'epic-name-custom-field-id': epicName});
    return this.executeRequest(
      progress,
      this._httpClient
        .put(path, payload, {
          headers: this.createHeaders()
        }));
  }

  private createHeaders(): HttpHeaders {
    return new HttpHeaders()
      .append('Content-Type', 'application/json');
  }

  private executeRequest<T>(progress: Progress, observable: Observable<T>): Observable<T> {
    return observable
      .pipe(
        timeout(this._timeout),
        catchError((response: HttpErrorResponse) => {
          progress.errorResponse(response);
          return _throw(response);
        }),
        tap(s => {
          progress.complete();
        })
      );
  }
}
