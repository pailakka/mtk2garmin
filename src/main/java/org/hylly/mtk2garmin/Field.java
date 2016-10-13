package org.hylly.mtk2garmin;

class Field {
	private String fname;
	private int fieldType;
	private int fieldIndex;

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
