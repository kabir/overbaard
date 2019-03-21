import {AffectsVersionActions, affectsVersionMetaReducer} from './affects-version.reducer';
import {AffectsVersionState, initialAffectsVersionState} from './affects-version.model';
import {cloneObject} from '../../../../common/object-util';

export function getTestAffectsVersionsInput(): any {
  return cloneObject(['A-10', 'A-20', 'A-30']);
}

export function getTestAffectsVersionState(): AffectsVersionState {
  const input: any = getTestAffectsVersionsInput();
  return affectsVersionMetaReducer(initialAffectsVersionState, AffectsVersionActions.createDeserializeAffectsVersions(input));
}

describe('Affects Version reducer tests', () => {
  describe('Deserialize', () => {
    it('Deserialize initial state', () => {
      const state: AffectsVersionState = getTestAffectsVersionState();
      expect(state.versions.toArray()).toEqual(['A-10', 'A-20', 'A-30']);
    });

    it ('Deserialize same state', () => {
      const stateA: AffectsVersionState = getTestAffectsVersionState();
      const stateB: AffectsVersionState =
        affectsVersionMetaReducer(stateA, AffectsVersionActions.createDeserializeAffectsVersions(getTestAffectsVersionsInput()));
      expect(stateA).toBe(stateB);
    });
  });

  describe('Changes', () => {
    it ('Add components', () => {
      const state: AffectsVersionState = getTestAffectsVersionState();
      const newState: AffectsVersionState =
        affectsVersionMetaReducer(state, AffectsVersionActions.createAddAffectsVersions(['a-05', 'A-14', 'a-13', 'A-25']));
      expect(newState.versions.toArray()).toEqual(['a-05', 'A-10', 'a-13', 'A-14', 'A-20', 'A-25', 'A-30']);
    });

    it ('No change', () => {
      const state: AffectsVersionState = getTestAffectsVersionState();
      const newState: AffectsVersionState =
        affectsVersionMetaReducer(state, AffectsVersionActions.createAddAffectsVersions(null));
      expect(newState).toBe(state);
    });
  });
});


