import {makeTypedFactory, TypedRecord} from 'typed-immutable-record';
import {Map, OrderedMap} from 'immutable';


export interface CustomFieldState {
  fields: OrderedMap<string, OrderedMap<string, CustomField>>;
  fieldMetadata: Map<string, CustomFieldMetadata>;
}

export interface CustomField {
  key: string;
  value: string;
}

export interface CustomFieldMetadata {
  type: string;
}

const DEFAULT_STATE: CustomFieldState = {
  fields: OrderedMap<string, OrderedMap<string, CustomField>>(),
  fieldMetadata: Map<string, CustomFieldMetadata>()
};

const DEFAULT_CUSTOM_FIELD: CustomField = {
  key: null,
  value: null
};

const DEFAULT_CUSTOM_FIELD_METADATA: CustomFieldMetadata = {
  type: null
};

interface CustomFieldStateRecord extends TypedRecord<CustomFieldStateRecord>, CustomFieldState {
}

interface CustomFieldRecord extends TypedRecord<CustomFieldRecord>, CustomField {
}

interface CustomFieldMetadataRecord extends TypedRecord<CustomFieldMetadataRecord>, CustomFieldMetadata {
}

const STATE_FACTORY = makeTypedFactory<CustomFieldState, CustomFieldStateRecord>(DEFAULT_STATE);
const CUSTOM_FIELD_FACTORY = makeTypedFactory<CustomField, CustomFieldRecord>(DEFAULT_CUSTOM_FIELD);
const CUSTOM_FIELD_METADATA_FACTORY = makeTypedFactory<CustomFieldMetadata, CustomFieldMetadataRecord>(DEFAULT_CUSTOM_FIELD_METADATA);
export const initialCustomFieldState: CustomFieldState = STATE_FACTORY(DEFAULT_STATE);

export class CustomFieldUtil {

  static metadataFromJs(input: any): CustomFieldMetadata {
    return CUSTOM_FIELD_METADATA_FACTORY(input);
  }

  static fromJs(input: any): CustomField {
      return CUSTOM_FIELD_FACTORY(input);
  }

  static withMutations(s: CustomFieldState, mutate: (mutable: CustomFieldState) => any): CustomFieldState {
    return (<CustomFieldStateRecord>s).withMutations(mutable => {
      mutate(mutable);
    });
  }
}


