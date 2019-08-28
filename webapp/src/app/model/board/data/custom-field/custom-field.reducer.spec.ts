import {CustomField, CustomFieldState, initialCustomFieldState} from './custom-field.model';
import {CustomFieldActions, customFieldMetaReducer} from './custom-field.reducer';
import {List, OrderedMap} from 'immutable';
import {cloneObject} from '../../../../common/object-util';

export function getTestCustomFieldsInput() {
  return cloneObject({
    'Custom-1': [
      {
        key: 'c1-A',
        value: 'First C1'
      },
      {
        key: 'c1-B',
        value: 'Second C1'
      },
      {
        key: 'c1-C',
        value: 'Third C1'
      }
    ],
    'Custom-2': [
      {
        key: 'c2-A',
        value: 'First C2'
      },
      {
        key: 'c2-B',
        value: 'Second C2'
      }
    ]
  });
}

function getTestCustomFieldsInputWithMetadata() {
  return cloneObject({
    'Custom-1': [
      {
        _metadata: {
          type: 'user'
        }
      },
      {
        key: 'c1-A',
        value: 'First C1'
      }
    ],
    'Custom-2': [
      {
        key: 'c2-A',
        value: 'First C2'
      }
    ],
    'Custom-3': [
      {
        _metadata: {
          type: 'thing'
        }
      },
      {
        key: 'c3-A',
        value: 'First C3'
      }
    ]
  });
}

export function getTestCustomFieldState(): CustomFieldState {
  const input: any = getTestCustomFieldsInput();
  return customFieldMetaReducer(initialCustomFieldState, CustomFieldActions.createDeserializeCustomFields(input));
}

function getTestCustomFieldStateWithMetadata(): CustomFieldState {
  const input: any = getTestCustomFieldsInputWithMetadata();
  return customFieldMetaReducer(initialCustomFieldState, CustomFieldActions.createDeserializeCustomFields(input));
}

describe('CustomField reducer tests', () => {
  describe('Deserialize', () => {
    describe('No metadata', () => {
      it('Deserialize initial state', () => {
        const state: CustomFieldState = getTestCustomFieldState();
        const map: OrderedMap<string, List<CustomField>> = state.fields.map(value => value.toList()).toOrderedMap();
        expect(map.size).toBe(2);
        expect(state.fieldMetadata.size).toBe(0);
        const l1 = map.get('Custom-1');
        expect(l1.size).toBe(3);
        expect(l1.get(0).key).toEqual('c1-A');
        expect(l1.get(0).value).toEqual('First C1');
        expect(l1.get(1).key).toEqual('c1-B');
        expect(l1.get(1).value).toEqual('Second C1');
        expect(l1.get(2).key).toEqual('c1-C');
        expect(l1.get(2).value).toEqual('Third C1');
        const l2 = map.get('Custom-2');
        expect(l2.size).toBe(2);
        expect(l2.get(0).key).toEqual('c2-A');
        expect(l2.get(0).value).toEqual('First C2');
        expect(l2.get(1).key).toEqual('c2-B');
        expect(l2.get(1).value).toEqual('Second C2');

      });

      it ('Deserialize same state', () => {
        const stateA: CustomFieldState = getTestCustomFieldState();
        const stateB: CustomFieldState =
          customFieldMetaReducer(stateA, CustomFieldActions.createDeserializeCustomFields(getTestCustomFieldsInput()));
        expect(stateA).toBe(stateB);
      });
    });

    describe('With metadata', () => {
      it('Deserialize initial state', () => {
        const state: CustomFieldState = getTestCustomFieldStateWithMetadata();
        const map: OrderedMap<string, List<CustomField>> = state.fields.map(value => value.toList()).toOrderedMap();
        expect(map.size).toBe(3);
        expect(state.fieldMetadata.size).toBe(2);

        const l1 = map.get('Custom-1');
        expect(l1.size).toBe(1);
        expect(l1.get(0).key).toEqual('c1-A');
        expect(l1.get(0).value).toEqual('First C1');
        expect(state.fieldMetadata.get('Custom-1').type).toBe('user', `expected ${state.fieldMetadata.get('Custom-1')} to equal user`);

        const l2 = map.get('Custom-2');
        expect(l2.size).toBe(1);
        expect(l2.get(0).key).toEqual('c2-A');
        expect(l2.get(0).value).toEqual('First C2');
        expect(state.fieldMetadata.get('Custom-2')).toBeFalsy();

        const l3 = map.get('Custom-3');
        expect(l3.size).toBe(1);
        expect(l3.get(0).key).toEqual('c3-A');
        expect(l3.get(0).value).toEqual('First C3');
        expect(state.fieldMetadata.get('Custom-3').type).toBe('thing');
      });

      it ('Deserialize same state', () => {
        const stateA: CustomFieldState = getTestCustomFieldStateWithMetadata();
        const stateB: CustomFieldState =
          customFieldMetaReducer(stateA, CustomFieldActions.createDeserializeCustomFields(getTestCustomFieldsInputWithMetadata()));
        expect(stateA).toBe(stateB);
      });
    });

  });

  describe('Changes', () => {
    it ('Add several custom fields', () => {
      const state: CustomFieldState = getTestCustomFieldState();
        customFieldMetaReducer(initialCustomFieldState, CustomFieldActions.createDeserializeCustomFields(getTestCustomFieldsInput()));
      const newState: CustomFieldState =
        customFieldMetaReducer(state, CustomFieldActions.createAddCustomFields({
          'Custom-1': [{key: 'c1-a', value: 'A'}, {key: 'c1-z', value: 'Z'}],
          'Custom-2': [{key: 'c2-k', value: 'k'}]}));
      const map: OrderedMap<string, List<CustomField>> = newState.fields.map(value => value.toList()).toOrderedMap();
      expect(map.size).toBe(2);
      const l1 = map.get('Custom-1');
      expect(l1.size).toBe(5);
      expect(l1.get(0).key).toEqual('c1-a');
      expect(l1.get(0).value).toEqual('A');
      expect(l1.get(1).key).toEqual('c1-A');
      expect(l1.get(1).value).toEqual('First C1');
      expect(l1.get(2).key).toEqual('c1-B');
      expect(l1.get(2).value).toEqual('Second C1');
      expect(l1.get(3).key).toEqual('c1-C');
      expect(l1.get(3).value).toEqual('Third C1');
      expect(l1.get(4).key).toEqual('c1-z');
      expect(l1.get(4).value).toEqual('Z');
      const l2 = map.get('Custom-2');
      expect(l2.size).toBe(3);
      expect(l2.get(0).key).toEqual('c2-A');
      expect(l2.get(0).value).toEqual('First C2');
      expect(l2.get(1).key).toEqual('c2-k');
      expect(l2.get(1).value).toEqual('k');
      expect(l2.get(2).key).toEqual('c2-B');
      expect(l2.get(2).value).toEqual('Second C2');
    });

    it ('Add one custom field', () => {
      const state: CustomFieldState = getTestCustomFieldState();
      const newState: CustomFieldState =
        customFieldMetaReducer(state, CustomFieldActions.createAddCustomFields({
          'Custom-2': [{key: 'c2-k', value: 'k'}]}));
      const map: OrderedMap<string, List<CustomField>> = newState.fields.map(value => value.toList()).toOrderedMap();
      expect(map.size).toBe(2);
      const l1 = map.get('Custom-1');
      expect(l1.size).toBe(3);
      expect(l1.get(0).key).toEqual('c1-A');
      expect(l1.get(0).value).toEqual('First C1');
      expect(l1.get(1).key).toEqual('c1-B');
      expect(l1.get(1).value).toEqual('Second C1');
      expect(l1.get(2).key).toEqual('c1-C');
      expect(l1.get(2).value).toEqual('Third C1');
      const l2 = map.get('Custom-2');
      expect(l2.size).toBe(3);
      expect(l2.get(0).key).toEqual('c2-A');
      expect(l2.get(0).value).toEqual('First C2');
      expect(l2.get(1).key).toEqual('c2-k');
      expect(l2.get(1).value).toEqual('k');
      expect(l2.get(2).key).toEqual('c2-B');
      expect(l2.get(2).value).toEqual('Second C2');
    });

    it ('No change', () => {
      const state: CustomFieldState = getTestCustomFieldState();
      const newState: CustomFieldState =
        customFieldMetaReducer(state, CustomFieldActions.createAddCustomFields(null));
      expect(newState).toBe(state);
    });
  });
});
