package org.hylly.mtk2garmin;

public class Field {
	private String fname;
	private int fieldType;
	private int fieldIndex;
	
	public Field(String fname, int fieldType, int fieldIndex) {
		this.fname = fname;
		this.fieldType = fieldType;
		this.fieldIndex = fieldIndex;
	}
	
	public String getFieldName() {
		return this.fname;
	}
	
	public int getFieldType() {
		return this.fieldType;
	}

	public int getFieldIndex() {
		return fieldIndex;
	}

}
