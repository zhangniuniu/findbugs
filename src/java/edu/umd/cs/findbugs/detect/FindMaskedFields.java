/*
 * FindBugs - Find bugs in Java programs
 * Copyright (C) 2003,2004 University of Maryland
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package edu.umd.cs.findbugs.detect;
import java.util.*;
import edu.umd.cs.findbugs.*;
import org.apache.bcel.classfile.*;
import org.apache.bcel.generic.Type;
import org.apache.bcel.util.Repository;
import edu.umd.cs.findbugs.visitclass.Constants2;

public class FindMaskedFields extends BytecodeScanningDetector implements Constants2 {
	private BugReporter bugReporter;
	private int numParms;
	private Set<String> classFields;

	public FindMaskedFields(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
		classFields = new HashSet<String>();
	}
	
	public void visit(JavaClass obj) {
		if (obj.isInterface())
			return;
		classFields.clear();
		
		Field[] fields = obj.getFields();
		String fieldName;
		for (int f = 0; f < fields.length; f++) {
			fieldName = fields[f].getName();
			classFields.add(fieldName);
		}
		
		// Walk up the super class chain, looking for name collisions
		try
		{	
			JavaClass[] superClasses = org.apache.bcel.Repository.getSuperClasses(obj);
			if (superClasses.length > 0) {
				Set<String> superClassFields = new HashSet<String>();
				for (int c = 0; c < superClasses.length; c++) {
					fields = superClasses[c].getFields();
					for (int f = 0; f < fields.length; f++) {
						Field fld = fields[f];
						if (fld.isPublic() || fld.isProtected()) {
							fieldName = fld.getName();
							if (fieldName.equals("serialVersionUID"))
								continue;
							if (classFields.contains( fieldName )) {
								FieldAnnotation fa = new FieldAnnotation( getDottedClassName(), 
													  fieldName, 
													  fld.getSignature(), 
													  fld.isStatic());
				                                bugReporter.reportBug(
									new BugInstance("MF_CLASS_MASKS_FIELD", LOW_PRIORITY)
				                                        	.addClass(this)
				                                        	.addField(fa));
							}
							else
								superClassFields.add(fieldName);
						}
					}
				}
				classFields.addAll(superClassFields);
			}
		}
		catch (ClassNotFoundException e) { 
			bugReporter. reportMissingClass(e);
		}
		
		super.visit(obj);
	}
	
	public void visit(Method obj) {
		super.visit(obj);
		numParms = obj.getArgumentTypes().length;
	}
	
	public void visit(LocalVariableTable obj) {
		LocalVariable[] vars = obj.getLocalVariableTable();
		for (int v = numParms+1; v < vars.length; v++) { 
			LocalVariable var = vars[v];
			String varName = var.getName();
			if (varName.equals("serialVersionUID"))
				continue;
			if (classFields.contains(varName)) {
				FieldAnnotation fa = new FieldAnnotation( getDottedClassName(), 
										varName, 
										var.getSignature(), 
										false);
				MethodAnnotation ma = MethodAnnotation.fromVisitedMethod(this);
				if (var.getStartPC() > 0)
		                        bugReporter.reportBug(
						new BugInstance("MF_METHOD_MASKS_FIELD", NORMAL_PRIORITY)
		                                	.addClass(this)
		                                	.addMethod(ma)
		                                	.addField(fa)
		                                	.addSourceLine(this, var.getStartPC()-1));
			}
		}
		super.visit(obj);
	}
}