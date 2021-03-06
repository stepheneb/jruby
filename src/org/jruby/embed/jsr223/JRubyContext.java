/**
 * **** BEGIN LICENSE BLOCK *****
 * Version: CPL 1.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Common Public
 * License Version 1.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/cpl-v10.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2009-2010 Yoko Harada <yokolet@gmail.com>
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the CPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the CPL, the GPL or the LGPL.
 * **** END LICENSE BLOCK *****
 */
package org.jruby.embed.jsr223;

import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.SimpleBindings;
import org.jruby.embed.ScriptingContainer;

/**
 * Implementation of javax.script.ScriptContext.
 * 
 * @author Yoko Harada <yokolet@gmail.com>
 */
class JRubyContext implements ScriptContext {
    private ScriptingContainer container;
    private final List<Integer> scopeList;
    private Bindings globalMap = null;
    private Bindings engineMap;

    public enum Scope {

        ENGINE(ScriptContext.ENGINE_SCOPE),
        GLOBAL(ScriptContext.GLOBAL_SCOPE);
        private final int priority;

        Scope(int priority) {
            this.priority = priority;
        }

        int getPriority() {
            return priority;
        }
    }

    static JRubyContext convert(ScriptingContainer container, ScriptContext context) {
        if (context instanceof JRubyContext) return (JRubyContext) context;
        JRubyContext tmpContext = new JRubyContext(container);
        tmpContext.setWriter(context.getWriter());
        tmpContext.setErrorWriter(context.getErrorWriter());
        tmpContext.setReader(context.getReader(), false);
        tmpContext.setEngineScopeBindings(context.getBindings(ScriptContext.ENGINE_SCOPE));
        tmpContext.setGlobalScopeBindings(context.getBindings(ScriptContext.GLOBAL_SCOPE));
        return tmpContext;
    }

    // update ScriptContext by JRubyContext after eval
    static void update(JRubyContext jrubyContext, ScriptContext context) {
        if (jrubyContext == null || context == null) return;
        Bindings tmpBindings = jrubyContext.getEngineScopeBindings();
        Bindings bindings = context.getBindings(ScriptContext.ENGINE_SCOPE);
        updateBindings(tmpBindings, bindings);
        tmpBindings = jrubyContext.getGlobalScopeBindings();
        if (tmpBindings == null) return;
        bindings = context.getBindings(ScriptContext.GLOBAL_SCOPE);
        updateBindings(tmpBindings, bindings);
    }

    private static void updateBindings(Bindings tmpBindings, Bindings bindings) {
        if (tmpBindings == bindings) return;
        Set<String> keys = tmpBindings.keySet();
        for (String key : keys) {
            Object value = tmpBindings.get(key);
            bindings.put(key, value);
        }
    }

    JRubyContext(ScriptingContainer container) {
        this.container = container;
        List<Integer> list = new ArrayList<Integer>();
        for (Scope scope : Scope.values()) {
            list.add(scope.getPriority());
        }
        scopeList = Collections.unmodifiableList(list);
        engineMap = new SimpleBindings();
    }

    private void checkName(String name) {
        if (name == null) {
            throw new NullPointerException("name is null");
        }
        if (name.length() == 0) {
            throw new IllegalArgumentException("name is empty");
        }
    }

    public Object getAttribute(String name) {
        Object ret = null;
        for (Scope scope : Scope.values()) {
            ret = getAttributeFromScope(scope.getPriority(), name);
            if (ret != null) {
                return ret;
            }
        }
        return ret;
    }

    private Object getAttributeFromScope(int priority, String name) {
        checkName(name);
        Object value;
        if (priority == Scope.ENGINE.getPriority()) {
            value = engineMap.get(name);
            if (value == null && Utils.isRubyVariable(container, name)) {
                value = container.get(Utils.getReceiver(this), name);
                engineMap.put(name, value);
            }
            return value;
        } else if (priority == Scope.GLOBAL.getPriority()) {
            if (globalMap == null) {
                return null;
            }
            return globalMap.get(name);
        } else {
            throw new IllegalArgumentException("invalid scope");
        }
    }

    public Object getAttribute(String name, int scope) {
        return getAttributeFromScope(scope, name);
    }

    public int getAttributesScope(String name) {
        for (Scope scope : Scope.values()) {
            Object ret = getAttributeFromScope(scope.getPriority(), name);
            if (ret != null) {
                return scope.getPriority();
            }
        }
        return -1;
    }

    public Bindings getBindings(int priority) {
        if (priority == Scope.ENGINE.getPriority()) {
            return engineMap;
        } else if (priority == Scope.GLOBAL.getPriority()) {
            return globalMap;
        } else {
            throw new IllegalArgumentException("invalid scope");
        }
    }

    Bindings getEngineScopeBindings() {
        return engineMap;
    }

    Bindings getGlobalScopeBindings() {
        return globalMap;
    }

    public Writer getErrorWriter() {
        return (Writer) container.getErrorWriter();
    }

    public Reader getReader() {
        return (Reader) container.getReader();
    }

    public List<Integer> getScopes() {
        return scopeList;
    }

    public Writer getWriter() {
        return (Writer) container.getWriter();
    }

    public Object removeAttribute(String name, int priority) {
        checkName(name);
        Bindings bindings = getBindings(priority);
        if (bindings == null) {
            return null;
        }
        return bindings.remove(name);
    }

    public void setAttribute(String key, Object value, int priority) {
        Bindings bindings = getBindings(priority);
        if (bindings == null) {
            return;
        }
        bindings.put(key, value);
    }

    public void setBindings(Bindings bindings, int scope) {
        if (scope == Scope.ENGINE.getPriority() && bindings == null) {
            throw new NullPointerException("null bindings in ENGINE scope");
        }
        if (scope == Scope.ENGINE.getPriority()) {
            engineMap = bindings;
        } else if (scope == Scope.GLOBAL.getPriority()) {
            globalMap = bindings;
        } else {
            throw new IllegalArgumentException("invalid scope");
        }
    }

    void setEngineScopeBindings(Bindings bindings) {
        engineMap = bindings;
    }

    void setGlobalScopeBindings(Bindings bindings) {
        globalMap = bindings;
    }

    public void setErrorWriter(Writer errorWriter) {
        if (errorWriter == null) {
            return;
        }
        if (getErrorWriter() == errorWriter) {
            return;
        }
        container.setErrorWriter(errorWriter);
    }

    public void setReader(Reader reader) {
        setReader(reader, true);
    }

    void setReader(Reader reader, boolean updateContainer) {
        if (reader == null) {
            return;
        }
        if (getReader() == reader) {
            return;
        }
        if (updateContainer) container.setReader(reader);
    }

    public void setWriter(Writer writer) {
        if (writer == null) {
            return;
        }
        if (getWriter() == writer) {
            return;
        }
        container.setWriter(writer);
    }
}
