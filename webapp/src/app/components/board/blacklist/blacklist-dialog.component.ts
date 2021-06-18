import {ChangeDetectionStrategy, Component, Inject, OnInit} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {BlacklistState} from '../../../model/board/data/blacklist/blacklist.model';
import {UrlService} from '../../../services/url.service';

@Component({
  selector: 'app-blacklist-dialog',
  templateUrl: './blacklist-dialog.component.html',
  styleUrls: ['./blacklist-dialog.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BlacklistDialogComponent {

  blacklist: BlacklistState;
  constructor(
    public dialogRef: MatDialogRef<BlacklistDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: any,
    private _urlService: UrlService) {

    this.blacklist = data['blacklist'];
  }

  issueUrl(key: string): string {
    return this._urlService.jiraUrl + 'browse/' + key;
  }
}
