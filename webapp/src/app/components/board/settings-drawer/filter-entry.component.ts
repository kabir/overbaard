import {ChangeDetectionStrategy, Component, EventEmitter, Input, OnDestroy, OnInit, Output} from '@angular/core';
import {FilterAttributes, PARALLEL_TASK_ATTRIBUTES} from '../../../model/board/user/board-filter/board-filter.constants';
import {List, Set} from 'immutable';
import {Dictionary} from '../../../common/dictionary';
import {FilterFormEntry} from '../../../common/filter-form-entry';
import {BoardFilterState} from '../../../model/board/user/board-filter/board-filter.model';
import {UntypedFormGroup} from '@angular/forms';
import {FilterEntryEvent} from './filter-entry.event';
import {Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';
import {getNonParallelTaskSet} from './settings-drawer.util';


@Component({
  selector: 'app-setting-filter-entry',
  templateUrl: './filter-entry.component.html',
  styleUrls: ['./filter-entry.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FilterEntryComponent implements OnInit, OnDestroy {

  @Input()
  filterAttributes: FilterAttributes;

  @Input()
  selected: boolean;

  @Input()
  filters: BoardFilterState;

  @Input()
  filterEntryDictionaryEntry: Dictionary<FilterFormEntry>;

  @Input()
  filterForm: UntypedFormGroup;

  @Input()
  filterEntries: FilterFormEntry[];

  @Output()
  filterEntryEvent: EventEmitter<FilterEntryEvent> = new EventEmitter<FilterEntryEvent>();


  private tooltip: string;

  filterSearch: string;

  issueList: List<string> = List<string>();

  destroy$: Subject<void> = new Subject<void>();

  ngOnInit(): void {
    this.filterForm.valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe(v => {
      this.tooltip = null;
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next(null);
  }

  onOpenFilterPanel() {
    this.filterSearch = null;
    this.filterEntryEvent.emit(FilterEntryEvent.OPENED_ENTRY);
  }

  onCloseFilterPanel() {
    this.filterEntryEvent.emit(FilterEntryEvent.CLOSED_ENTRY);
  }

  onClearFilter(event: MouseEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.filterEntryEvent.emit(FilterEntryEvent.CLEARED_FILTER);
  }

  onInvertFilter() {
    this.filterEntryEvent.emit(FilterEntryEvent.INVERTED_FILTER);
  }

  onSelectAllFilter(filterAttributes: FilterAttributes) {
    this.filterEntryEvent.emit(FilterEntryEvent.SELECTED_ALL_FILTER);
  }

  getSelectionTooltip(): string {
    let tooltip: string = this.tooltip;
    if (!tooltip) {
      tooltip = this.createSelectionTooltip();
      if (tooltip.length > 0) {
        tooltip = this.filterAttributes.display + '\n\n' + tooltip;
      }
      this.tooltip = tooltip;
    }
    return tooltip;
  }

  createSelectionTooltip(): string {
    const set: Set<string> = getNonParallelTaskSet(this.filters, this.filterAttributes);
    if (set && set.size > 0) {
      const lookup: Dictionary<FilterFormEntry> = this.filterEntryDictionaryEntry;
      let first = true;
      let tooltip = '';
      set.forEach(key => {
        // There might be strange state in the redux store from the querystring (say if an assignee no longer exists on the board).
        // Check these entries are there before attempting to use the tooltip
        const entry: FilterFormEntry = lookup[key];
        if (entry !== null && entry !== undefined) {
          if (first) {
            first = false;
          } else {
            tooltip += '\n';
          }
          tooltip += entry.display;
        }
      });
      return tooltip;
    }
    if (this.filterAttributes === PARALLEL_TASK_ATTRIBUTES) {
      let first = true;
      let tooltip = '';
      const taskEntries: FilterFormEntry[] = this.filterEntries;
      for (const taskEntry of taskEntries) {
        const taskSet: Set<string> = this.filters.parallelTask.get(taskEntry.key);
        if (taskSet && taskSet.size > 0) {
          if (first) {
            first = false;
          } else {
            tooltip += '\n\n';
          }
          tooltip += taskEntry.display + ':';
          const childKeys: Set<string> = Set<string>(taskEntry.children.map(c => c.key));
          taskSet.forEach(key => {
            // Only display actual existing children (in case of stale links)
            if (childKeys.contains(key)) {
              tooltip += '\n' + key;
            }
          });

        }
      }
      return tooltip;
    }
    return '';
  }
}
