/*
 * #%L
 * JSR-223-compliant Groovy scripting language plugin.
 * %%
 * Copyright (C) 2014 - 2017 Board of Regents of the University of
 * Wisconsin-Madison.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package org.scijava.plugins.scripting.groovy;

import groovy.lang.GroovySystem;

import java.util.*;

import javax.script.ScriptEngine;

import org.scijava.Priority;
import org.scijava.command.CommandService;
import org.scijava.module.ModuleService;
import org.scijava.plugin.Plugin;
import org.scijava.script.AbstractScriptLanguage;
import org.scijava.script.ScriptLanguage;

/**
 * An adapter of the Groovy interpreter to the SciJava scripting interface.
 *
 * @author Mike Grogan
 * @author A. Sundararajan
 * @author Curtis Rueden
 * @see ScriptEngine
 */
@Plugin(type = ScriptLanguage.class, name = "Groovy",
	priority = Priority.HIGH_PRIORITY)
public class GroovyScriptLanguage extends AbstractScriptLanguage {

	private static String VERSION = "1.5.6";

	private Map<Class, String> parameters = new HashMap<>();

	// -- ScriptEngineFactory methods --

	@Override
	public String getEngineName() {
		return "groovy";
	}

	@Override
	public String getEngineVersion() {
		return GroovySystem.getVersion();
	}

	@Override
	public String getLanguageVersion() {
		return VERSION;
	}

	@Override
	public List<String> getExtensions() {
		return extensions;
	}

	@Override
	public List<String> getMimeTypes() {
		return mimeTypes;
	}

	@Override
	public List<String> getNames() {
		return names;
	}

	@Override
	public Object getParameter(final String key) {

		if (ScriptEngine.NAME.equals(key)) {
			return "Groovy";
		}
		else if (ScriptEngine.ENGINE.equals(key)) {
			return "Groovy Script Engine";
		}
		else if (ScriptEngine.ENGINE_VERSION.equals(key)) {
			return GroovySystem.getVersion();
		}
		else if (ScriptEngine.LANGUAGE.equals(key)) {
			return "Groovy";
		}
		else if (ScriptEngine.LANGUAGE_VERSION.equals(key)) {
			return VERSION;
		}
		else if ("THREADING".equals(key)) {
			return "MULTITHREADED";
		}
		else {
			throw new IllegalArgumentException("Invalid key");
		}

	}

	@Override
	public ScriptEngine getScriptEngine() {
		return new GroovyScriptEngine();
	}

	@Override
	public String getMethodCallSyntax(final String obj, final String method,
		final String... args)
	{

		String ret = obj + "." + method + "(";
		final int len = args.length;
		if (len == 0) {
			ret += ")";
			return ret;
		}

		for (int i = 0; i < len; i++) {
			ret += args[i];
			if (i != len - 1) {
				ret += ",";
			}
			else {
				ret += ")";
			}
		}
		return ret;
	}

	@Override
	public String getOutputStatement(final String toDisplay) {
		final StringBuilder buf = new StringBuilder();
		buf.append("println(\"");
		final int len = toDisplay.length();
		for (int i = 0; i < len; i++) {
			final char ch = toDisplay.charAt(i);
			switch (ch) {
				case '"':
					buf.append("\\\"");
					break;
				case '\\':
					buf.append("\\\\");
					break;
				default:
					buf.append(ch);
					break;
			}
		}
		buf.append("\")");
		return buf.toString();
	}

	@Override
	public String getProgram(final String... statements) {
		final StringBuilder ret = new StringBuilder();
		final int len = statements.length;
		for (int i = 0; i < len; i++) {
			ret.append(statements[i]);
			ret.append('\n');
		}
		return ret.toString();
	}


	@Override
	public void registerParameter(Class objectClass, String objectVariableName) {
		if(!parameters.containsKey(objectClass)) parameters.put(objectClass, objectVariableName);
	}

	@Override
	public String encodeParameter(Class objectClass) {
		return "// @" + objectClass.getSimpleName() + " " + getScriptParameter(objectClass);
	}

	private String getScriptParameter(Class objectClass) {
		if(!parameters.containsKey(objectClass)) throw new NullPointerException("Parameter of type " + objectClass + " not known to language. Use registerParameter() first.");
		return parameters.get(objectClass);
	}

	@Override
	public String encodeUnknownVariable(final String variable)
	{
		return variable + " = ?";
	}

	@Override
	public String encodeVariableFromService(String variableName, String serviceVariableName, final String serviceMethodName)
	{
		return variableName + " = " + serviceVariableName + "." + serviceMethodName + "()";
	}

	@Override
	public String encodeModuleCall(final String moduleName, boolean process, Map<String, Object> inputs, Map<String, String> outputs, Map<Object, String> variables)
	{
		String res = "";
		if(outputs.size() == 1) {
			res += outputs.keySet().toArray()[0] + " = ";
		}
		if(outputs.size() > 1) {
			res += "modfuture = ";
		}
		res += encodeCommandRun(moduleName, process, inputs);
		if(outputs.size() == 1) {
			res += ".get().getOutput(\"" + outputs.values().toArray()[0] + "\")";
		}
		res += encodeOutputVariables(outputs);
		return res;
	}

	private String encodeCommandRun(String command, boolean process, Map<String, Object> inputs) {
		String res = getScriptParameter(CommandService.class) + ".run(\"" + command + "\", ";
		res += process ? "True" : "False";
		if(inputs != null) {
			for (Map.Entry<String,Object> entry : inputs.entrySet()) {
				res += ", \"" + entry.getKey() + "\", " + entry.getValue();
			}
		}
		res += ")";
		return res;
	}

	private String encodeOutputVariables(Map<String, String> outputs) {
		if(outputs.size() < 2) return "";
		String res = "";
		res += "\nmodres = " + getScriptParameter(ModuleService.class) + ".waitFor(modfuture)";
		for (Map.Entry<String,String> entry : outputs.entrySet()) {
			res += "\n" + entry.getKey() + " = modres.getOutput(\"" + entry.getValue() + "\")";
		}
		return res;
	}

	private static List<String> names;
	private static List<String> extensions;
	private static List<String> mimeTypes;

	static {
		names = new ArrayList<String>(1);
		names.add("groovy");
		names = Collections.unmodifiableList(names);

		extensions = names;

		mimeTypes = new ArrayList<String>(0);
		mimeTypes = Collections.unmodifiableList(mimeTypes);
	}
}
