// CloudCoder - a web-based pedagogical programming environment
// Copyright (C) 2011-2016, Jaime Spacco <jspacco@knox.edu>
// Copyright (C) 2011-2016, David H. Hovemeyer <david.hovemeyer@gmail.com>
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.

package org.cloudcoder.progsnap2;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

public class ProgSnap2Event {
	//private static final int EVENT_ID_SPACING = 20;

	/**
	 * Metadata for a main event table column: specifically, its name
	 * and Java datatype.
	 */
	public static class Field {
		public final String name;
		public final Class<?> type;
		public Field(String name, Class<?> type) {
			this.name = name;
			this.type = type;
		}
		
		/**
		 * Convert a field value to a String (for writing to a CSV file.)
		 * 
		 * @param value the field value
		 * @return String representation of the field value
		 */
		public String convertToString(Object value) {
			return (value != null) ? value.toString() : "";
		}
	}

	/**
	 * ProgSnap2 main event table columns that the CloudCoder exporter generates.
	 */
	public static Field[] FIELDS = {
			new Field("EventType", EventType.class),
			new Field("EventID", Long.class),
			new Field("Order", Long.class),
			new Field("SubjectID", Integer.class),
			new Field("ToolInstances", String[].class) {
				@Override
				public String convertToString(Object value) {
					String[] toolInstances = (String[]) value;
					StringBuilder buf = new StringBuilder();
					for (int i = 0; i < toolInstances.length; i++) {
						if (i > 0) {
							buf.append("; ");
						}
						buf.append(toolInstances[i]);
					}
					return buf.toString();
				}
			},
			new Field("ParentEventID", Long.class),
			new Field("ServerTimestamp", Long.class) {
				@Override
				public String convertToString(Object value) {
					if (value == null) {
						throw new IllegalStateException("WTF?");
					}
					
					Long serverTimestamp = (Long) value;

					DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
					// note that timezone won't be part of the output; it's just
					// used to generate the correct local time from the absolute
					// numeric timestamp value
					df.setTimeZone(TimeZone.getTimeZone(Export.getExport().getServerTimezone()));
					return df.format(new Date(serverTimestamp));
				}
			},
			new Field("ServerTimezone", String.class),
			new Field("SessionID", Long.class),
			new Field("CourseID", Integer.class),
			new Field("CourseSectionID", Integer.class),
			new Field("TermID", String.class),
			//new Field("AssignmentID", Integer.class),
			//new Field("ResourceID", Integer.class),
			new Field("ProblemID", Integer.class),
			//new Field("ExperimentalCondition", String.class),
			//new Field("TeamID", Integer.class),
			new Field("TestID", String.class),
			new Field("ProgramResult", ProgramResult.class),
			new Field("EventInitiator", EventInitiator.class),
			new Field("ProgramInput", String.class),
			new Field("ProgramOutput", String.class),
			new Field("CodeStateID", String.class),
			new Field("CodeStateSection", String.class),
			new Field("ExecutionID", String.class),
			new Field("Score", Double.class),
	};
	
	/**
	 * Map of field names to {@link Field}s, for quick access.
	 */
	public static final Map<String, Field> FIELD_MAP = new HashMap<String, ProgSnap2Event.Field>();
	static {
		for (Field f : FIELDS) {
			FIELD_MAP.put(f.name, f);
		}
	}

	private Map<String, Object> fieldValues;
	
	/**
	 * Column names.
	 */
	public static final String[] COLUMN_NAMES = new String[FIELDS.length];
	static {
		for (int i = 0; i < FIELDS.length; i++) {
			COLUMN_NAMES[i] = FIELDS[i].name;
		}
	}

	private ProgSnap2Event(EventType eventType, long eventId, int subjectId, long sessionId, String[] toolInstances) {
		this.fieldValues = new HashMap<String, Object>();

		this.setFieldValue("EventType", eventType);
		// CloudCoder "native" event ids are multiplied by 40 in order to create
		// some space for ProgSnap 2 events that originate from a single
		// CloudCoder event.
		this.setFieldValue("EventID", eventId);
		this.setFieldValue("SubjectID", subjectId);
		this.setFieldValue("SessionID", sessionId);
		this.setFieldValue("ToolInstances", toolInstances);
		this.setFieldValue("ServerTimezone", Export.getExport().getServerTimezone());
	}
	
	/**
	 * Create a {@link ProgSnap2Event}.
	 * 
	 * @param eventType      the event type
	 * @param eventId        the event id
	 * @param subjectId      the subject id
	 * @param toolInstances  the tool instances
	 */
	public static ProgSnap2Event create(EventType eventType, long eventId, int subjectId, long sessionId, String[] toolInstances) {
		return new ProgSnap2Event(eventType, eventId, subjectId, sessionId, toolInstances);
	}
	
	public<E> void setFieldValue(String fieldName, E value) {
		Field f = FIELD_MAP.get(fieldName);
		if (f == null) {
			throw new IllegalArgumentException("Unknown field: " + fieldName);
		}
		if (value.getClass() != f.type) {
			throw new IllegalArgumentException(
					value.getClass().getSimpleName() + " is wrong type for " +
					f.type.getSimpleName() + " field");
		}
		fieldValues.put(f.name, value);
	}
	
	public<E> E getFieldValue(String fieldName, Class<E> cls) {
		Field f = FIELD_MAP.get(fieldName);
		if (f == null) {
			throw new IllegalArgumentException("Unknown field: " + fieldName);
		}
		Object value = fieldValues.get(fieldName);
		if (value == null) {
			return null;
		}
		if (cls != f.type) {
			throw new IllegalStateException("Field retrieval type mismatch (wanted " + cls.getSimpleName() +
					", got " + f.type.getSimpleName() + ")");
		}
		return cls.cast(value);
	}

	public String[] toStrings() {
		String[] result = new String[FIELDS.length];
		for (int i = 0; i < FIELDS.length; i++) {
			Field f = FIELDS[i];
			result[i] = f.convertToString(fieldValues.get(f.name));
		}
		return result;
	}

	public void setServerTimestamp(long timestamp) {
		setFieldValue("ServerTimestamp", timestamp);
	}

	public void setProblemId(Integer problemId) {
		setFieldValue("ProblemID", problemId);
	}

	public void setCourseId(Integer courseId) {
		setFieldValue("CourseID", courseId);
	}

	public void setEventInitiator(EventInitiator eventInitiator) {
		setFieldValue("EventInitiator", eventInitiator);
	}

	public void setProgramResult(ProgramResult programResult) {
		setFieldValue("ProgramResult", programResult);
	}

	public void setParentEventId(long parentEventId) {
		setFieldValue("ParentEventID", parentEventId);
	}

	public void setProgramInput(String programInput) {
		setFieldValue("ProgramInput", programInput);
	}

	public void setProgramOutput(String programOutput) {
		setFieldValue("ProgramOutput", programOutput);
	}

	public void setCodeStateId(String codeStateId) {
		setFieldValue("CodeStateID", codeStateId);
	}

	public void setExecutionId(String executionId) {
		setFieldValue("ExecutionID", executionId);
	}
	
	public void setCodeStateSection(String codeStateSection) {
		setFieldValue("CodeStateSection", codeStateSection);
	}
	
	public void setScore(double score) {
		setFieldValue("Score", score);
	}
	
	public void setTestId(String testId) {
		setFieldValue("TestID", testId);
	}
	
	public void setOrder(long order) {
		setFieldValue("Order", order);
	}
	
	public void setTermId(String termId) {
		setFieldValue("TermID", termId);
	}

	public String getCodeStateId() {
		return getFieldValue("CodeStateID", String.class);
	}
	
	public Long getEventId() {
		return getFieldValue("EventID", Long.class);
	}
}