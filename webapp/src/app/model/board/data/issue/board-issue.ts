import {Assignee} from '../assignee/assignee.model';
import {Priority} from '../priority/priority.model';
import {IssueType} from '../issue-type/issue-type.model';
import {List, Map, OrderedSet} from 'immutable';
import {CustomField} from '../custom-field/custom-field.model';
import {Issue} from './issue';

export interface BoardIssue extends Issue {
  assignee: Assignee;
  priority: Priority;
  type: IssueType;
  components: OrderedSet<string>;
  labels: OrderedSet<string>;
  fixVersions: OrderedSet<string>;
  customFields: Map<string, CustomField>;
  parallelTasks: List<string>;
  linkedIssues: List<Issue>;
  ownState: number;

}