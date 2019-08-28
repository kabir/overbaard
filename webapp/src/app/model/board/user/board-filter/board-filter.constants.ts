import {makeTypedFactory, TypedRecord} from 'typed-immutable-record';
import {Map} from 'immutable';
import {CustomFieldMetadata} from '../../data/custom-field/custom-field.model';

export interface FilterAttributes {
  display: string;
  key: string;
  hasNone: boolean;
  hasCurrentUser: boolean;
  customField: boolean;
  swimlaneOption: boolean;
}

const DEFAULT_ATTRIBUTES: FilterAttributes = {
  display: '',
  key: '',
  hasNone: false,
  hasCurrentUser: false,
  customField: false,
  swimlaneOption: false
};

export const NONE_FILTER_KEY = '$n$o$n$e$';
export const CURRENT_USER_FILTER_KEY = '$cu$u$r$r$e$n$t$';

interface FilterAttributesRecord extends TypedRecord<FilterAttributesRecord>, FilterAttributes {
}

const FACTORY = makeTypedFactory<FilterAttributes, FilterAttributesRecord>(DEFAULT_ATTRIBUTES);

export const PROJECT_ATTRIBUTES = FACTORY(FilterAttributes('Project', 'project', false, false, false, true));
export const ISSUE_TYPE_ATTRIBUTES = FACTORY(FilterAttributes('Issue Type', 'issue-type', false, false, false, true));
export const PRIORITY_ATTRIBUTES = FACTORY(FilterAttributes('Priority', 'priority', false, false, false, true));
export const ASSIGNEE_ATTRIBUTES = FACTORY(FilterAttributes('Assignee', 'assignee', true, true, false, true));
export const COMPONENT_ATTRIBUTES = FACTORY(FilterAttributes('Component', 'component', true, false, false, true));
export const LABEL_ATTRIBUTES = FACTORY(FilterAttributes('Label', 'label', true, false, false, true));
export const FIX_VERSION_ATTRIBUTES = FACTORY(FilterAttributes('Fix Version', 'fix-version', true, false, false, true));
export const PARALLEL_TASK_ATTRIBUTES = FACTORY(FilterAttributes('Parallel Tasks', 'parallel-tasks', true, false, false, false));

function FilterAttributes(display: string, key: string, hasNone: boolean, hasCurrentUser: boolean, customField: boolean, swimlaneOption: boolean): FilterAttributes {
  return {
    display: display,
    key: key,
    hasNone: hasNone,
    hasCurrentUser: hasCurrentUser,
    customField: customField,
    swimlaneOption: swimlaneOption
  };
}

export class FilterAttributesUtil {
  static createCustomFieldFilterAttributes(customFieldName: string, fieldMetadata: CustomFieldMetadata): FilterAttributes {
    const hasCurrentUser = (fieldMetadata && fieldMetadata.type === 'user');
    return FACTORY(FilterAttributes(customFieldName, customFieldName, true, hasCurrentUser, true, true));
  }
}
