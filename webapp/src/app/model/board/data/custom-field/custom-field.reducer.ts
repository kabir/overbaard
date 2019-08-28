import {Action, createSelector} from '@ngrx/store';
import {Map, OrderedMap} from 'immutable';
import {CustomField, CustomFieldMetadata, CustomFieldState, CustomFieldUtil, initialCustomFieldState} from './custom-field.model';
import {AppState} from '../../../../app-store';


const DESERIALIZE_ALL_CUSTOM_FIELDS = 'DESERIALIZE_ALL_CUSTOM_FIELDS';
const ADD_CUSTOM_FIELDS = 'ADD_CUSTOM_FIELDS';

class DeserializeCustomFieldsAction implements Action {
  readonly type = DESERIALIZE_ALL_CUSTOM_FIELDS;

  constructor(readonly payload: OrderedMap<string, OrderedMap<string, CustomField>>, readonly fieldMetadata: Map<string, CustomFieldMetadata>) {
  }
}

class AddCustomFieldsAction implements Action {
  readonly type = ADD_CUSTOM_FIELDS;

  constructor(readonly payload: Map<string, OrderedMap<string, CustomField>>) {
  }
}

export class CustomFieldActions {
  static createDeserializeCustomFields(input: any): Action {
    const keys: string[] = Object.keys(input).sort((a, b) => a.toLocaleLowerCase().localeCompare(b.toLocaleLowerCase()));
    const mutableFieldMetadatas: Map<string, CustomFieldMetadata> = Map<string, CustomFieldMetadata>().asMutable();

    const map: OrderedMap<string, OrderedMap<string, CustomField>>
      = OrderedMap<string, OrderedMap<string, CustomField>>().withMutations(mutable => {
      for (const key of keys) {
        let inputArray: any[] = input[key];
        // If we have any metadata about the field (e.g. to convey to the front-end the type of the field), it will be in a specially
        // formatted element at the start of the array
        if (inputArray.length > 1 && inputArray[0]['_metadata']) {
          const metadata: CustomFieldMetadata = CustomFieldUtil.metadataFromJs(inputArray[0]['_metadata']);
          mutableFieldMetadatas.set(key, metadata);
          inputArray = inputArray.slice(1, inputArray.length);
        }

        mutable.set(key, this.createMapFromInput(inputArray));
      }
    });

    return new DeserializeCustomFieldsAction(map, mutableFieldMetadatas.asImmutable());
  }

  static createAddCustomFields(input: any): Action {
    let map: OrderedMap<string, OrderedMap<string, CustomField>> = OrderedMap<string, OrderedMap<string, CustomField>>();
    const mutableFieldMetadatas: Map<string, CustomFieldMetadata> = Map<string, CustomFieldMetadata>().asMutable();
    if (input) {
      map = map.withMutations(mutable => {
        for (const key of Object.keys(input)) {
          const inputArray: any[] = input[key];
          mutable.set(key, this.createMapFromInput(inputArray));
        }
      });
    }

    return new AddCustomFieldsAction(map);
  }

  private static createMapFromInput(inputArray: any[]): OrderedMap<string, CustomField> {
    return OrderedMap<string, CustomField>().withMutations(mutable => {
      for (let i = 0 ; i < inputArray.length ; i++) {
        const cf = CustomFieldUtil.fromJs(inputArray[i]);
        mutable.set(cf.key, cf);
      }
    });
  }
}

// 'meta-reducer here means it is not called directly by the store, rather from the boardReducer
export function customFieldMetaReducer(state: CustomFieldState = initialCustomFieldState, action: Action): CustomFieldState {

  switch (action.type) {
    case DESERIALIZE_ALL_CUSTOM_FIELDS: {
      const scfa: DeserializeCustomFieldsAction = <DeserializeCustomFieldsAction>action;
      const newState = CustomFieldUtil.withMutations(state, mutable => {
        if (!mutable.fields.equals(scfa.payload)) {
          mutable.fields = scfa.payload;
        }
        if (!mutable.fieldMetadata.equals(scfa.fieldMetadata)) {
          mutable.fieldMetadata = scfa.fieldMetadata;
        }
      });
      return newState;
    }
    case ADD_CUSTOM_FIELDS: {
      const payload: Map<string, OrderedMap<string, CustomField>> = (<AddCustomFieldsAction>action).payload;
      if (payload.size > 0) {
        const fields = state.fields.withMutations(mutableFields => {
          payload.forEach((map, key) => {
            const customFields: OrderedMap<string, CustomField> = mutableFields.get(key).concat(map)
              .sort((a, b) => a.value.toLocaleLowerCase().localeCompare(b.value.toLocaleLowerCase())).toOrderedMap();
            mutableFields.set(
                key,
                customFields);
          });
        });
        return CustomFieldUtil.withMutations(state, mutable => {
          mutable.fields = fields;
        });
      }
      return state;
    }
    default:
      return state;
  }
}


export const getCustomFieldsState = (state: AppState) => state.board.customFields;
const getCustomFields = (state: CustomFieldState) => state.fields;


export const customFieldsSelector = createSelector(getCustomFieldsState, getCustomFields);
