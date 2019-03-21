import {initialAssigneeState} from './assignee/assignee.model';
import {initialIssueTypeState} from './issue-type/issue-type.model';
import {initialPriorityState} from './priority/priority.model';
import {initialComponentState} from './component/component.model';
import {initialLabelState} from './label/label.model';
import {initialFixVersionState} from './fix-version/fix-version.model';
import {initialCustomFieldState} from './custom-field/custom-field.model';
import {initialProjectState} from './project/project.model';
import {initialRankState} from './rank/rank.model';
import {initialIssueState} from './issue/issue.model';
import {initialBlacklistState} from './blacklist/blacklist.model';
import {initialHeaderState} from './header/header.model';
import {makeTypedFactory, TypedRecord} from 'typed-immutable-record';
import {BoardState} from './board';
import {initialManualSwimlaneState} from './manual-swimlane/manual-swimlane.model';
import {initialEpicState} from './epic/epic.model';
import {initialAffectsVersionState} from './affects-version/affects-version.model';

const DEFAULT_STATE: BoardState = {
  viewId: -1,
  currentUser: null,
  rankCustomFieldId: -1,
  headers: initialHeaderState,
  assignees: initialAssigneeState,
  issueTypes: initialIssueTypeState,
  priorities: initialPriorityState,
  components: initialComponentState,
  labels: initialLabelState,
  fixVersions: initialFixVersionState,
  affectsVersions: initialAffectsVersionState,
  customFields: initialCustomFieldState,
  epics: initialEpicState,
  projects: initialProjectState,
  ranks: initialRankState,
  issues: initialIssueState,
  blacklist: initialBlacklistState,
  manualSwimlanes: initialManualSwimlaneState
};

interface BoardStateRecord extends TypedRecord<BoardStateRecord>, BoardState {
}

const STATE_FACTORY = makeTypedFactory<BoardState, BoardStateRecord>(DEFAULT_STATE);
export const initialBoardState: BoardState = STATE_FACTORY(DEFAULT_STATE);

export class BoardUtil {
  static withMutations(s: BoardState, mutate: (mutable: BoardState) => any): BoardState {
    return (<BoardStateRecord>s).withMutations(mutable => {
      mutate(mutable);
    });
  }
}
