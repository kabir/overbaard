import {
  ChangeDetectionStrategy, Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChange,
  SimpleChanges
} from '@angular/core';
import {FixedHeaderView} from '../fixed-header-view';
import {BoardViewMode} from '../../../../model/board/user/board-view-mode';
import {UpdateParallelTaskEvent} from '../../../../events/update-parallel-task.event';

@Component({
  selector: 'app-rank-view',
  templateUrl: './rank-view.component.html',
  styleUrls: ['./rank-view.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RankViewComponent extends FixedHeaderView implements OnInit, OnChanges {

  readonly viewMode = BoardViewMode.RANK;

  @Output()
  updateParallelTask: EventEmitter<UpdateParallelTaskEvent> = new EventEmitter<UpdateParallelTaskEvent>();


  // Just an array here to be able to do 'for s of states; let i = index' in the template
  statesDummyArray: number[];

  constructor() {
    super();
  }

  ngOnInit() {
    this.createEmptyStatesDummyArray();
  }

  ngOnChanges(changes: SimpleChanges): void {
    super.ngOnChanges(changes);
    if (changes['board']) {
      const change: SimpleChange = changes['board'];
      if (change) {
        this.createEmptyStatesDummyArray();
      }
    }
  }

  private createEmptyStatesDummyArray() {
    const numberStates =
      this.board.headers.headersList.reduce((sum, header) => sum += header.stateIndices.size, 0);
    this.statesDummyArray = new Array<number>(numberStates);
  }

  onUpdateParallelTask(event: UpdateParallelTaskEvent) {
    this.updateParallelTask.emit(event);
  }

}
