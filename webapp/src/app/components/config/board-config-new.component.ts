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
import {BoardConfigEvent} from './board-config.event';

@Component({
  selector: 'app-board-configuration-new',
  templateUrl: './board-config-new.component.html',
  styleUrls: ['./board-config-new.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})

export class NewBoardConfigComponent implements OnInit, OnChanges {
  @Input()
  template: boolean;

  @Input()
  owningTemplateId: number;

  @Output()
  configEvent: EventEmitter<BoardConfigEvent> = new EventEmitter<BoardConfigEvent>();

  templateId: number;

  boardId: number;

  ngOnInit(): void {
    this.doInit();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['owningTemplateId'] || changes['template']) {
      this.doInit();
    }
  }

  private doInit(): void {
    if (this.template) {
      this.templateId = -1;
    } else {
      if (!isNaN(this.owningTemplateId)) {
        this.templateId = this.owningTemplateId;
      }
      this.boardId = -1;
    }
  }
}
