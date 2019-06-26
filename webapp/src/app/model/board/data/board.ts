import {HeaderState} from './header/header.state';
import {AssigneeState} from './assignee/assignee.model';
import {IssueTypeState} from './issue-type/issue-type.model';
import {PriorityState} from './priority/priority.model';
import {ComponentState} from './component/component.model';
import {LabelState} from './label/label.model';
import {FixVersionState} from './fix-version/fix-version.model';
import {CustomFieldState} from './custom-field/custom-field.model';
import {ProjectState} from './project/project.model';
import {RankState} from './rank/rank.model';
import {IssueState} from './issue/issue.model';
import {BlacklistState} from './blacklist/blacklist.model';
import {ManualSwimlaneState} from './manual-swimlane/manual-swimlane.model';
import {EpicState} from './epic/epic.model';
import {AffectsVersionState} from './affects-version/affects-version.model';

export interface BoardState {
  viewId: number;
  currentUser: string;
  rankCustomFieldId: number;
  headers: HeaderState;
  assignees: AssigneeState;
  issueTypes: IssueTypeState;
  priorities: PriorityState;
  components: ComponentState;
  labels: LabelState;
  fixVersions: FixVersionState;
  affectsVersions: AffectsVersionState;
  customFields: CustomFieldState;
  epics: EpicState;
  projects: ProjectState;
  ranks: RankState;
  issues: IssueState;
  blacklist: BlacklistState;
  manualSwimlanes: ManualSwimlaneState;
}
