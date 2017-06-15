package org.hylly.mtk2garmin;

class Field {
	private final String fname;
	private final int fieldType;
	private final int fieldIndex;

	Field(String fname, int fieldType, int fieldIndex) {
		this.fname = fname;
		this.fieldType = fieldType;
		this.fieldIndex = fieldIndex;
	}

	String getFieldName() {
		return this.fname;
	}

	int getFieldType() {
		return this.fieldType;
	}

	int getFieldIndex() {
		return fieldIndex;
	}

}
