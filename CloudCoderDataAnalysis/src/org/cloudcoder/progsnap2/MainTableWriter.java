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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Closeable;

import au.com.bytecode.opencsv.CSV;
import au.com.bytecode.opencsv.CSVWriter;

// TODO: Consider generalizing this to an interface for the other types of tables in the spec
public class MainTableWriter implements Closeable {
    private File baseDir;
    private CSVWriter csvWriter;

    public MainTableWriter(File baseDir) throws IOException {
    	this.baseDir = baseDir;
        File f = new File(baseDir, "MainTable.csv");

        // make sure parent directory exists
        File parent = f.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        FileOutputStream writer = new FileOutputStream(f);
        csvWriter = CSV.charset("UTF-8").create().writer(writer);
        csvWriter.writeNext(ProgSnap2Event.COLUMN_NAMES);
    }
    
    public File getBaseDir() {
		return baseDir;
	}

    public void writeEvent(ProgSnap2Event event, String currentCodeStateId) {
    	event.setCodeStateId(currentCodeStateId);
        csvWriter.writeNext(event.toStrings());
    }

    public void writeEvent(ProgSnap2Event event) {
    	if (event.getCodeStateId() == null) {
    		throw new IllegalStateException("Writing event id " + event.getEventId() + " without a CodeStateID");
    	}
        csvWriter.writeNext(event.toStrings());
    }

    @Override
    public void close() throws IOException {
        csvWriter.close();
    }
    
    public File makeSubdir(String path) {
    	File subdir = new File(baseDir, path);
    	if (!subdir.mkdirs() && (!subdir.exists() || !subdir.isDirectory())) {
    		throw new RuntimeException("Error creating subdirectory " + path);
    	}
    	return subdir;
    }
}