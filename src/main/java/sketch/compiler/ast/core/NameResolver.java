package sketch.compiler.ast.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import sketch.compiler.ast.core.typs.TypeStruct;
import sketch.util.exceptions.UnrecognizedVariableException;

public class NameResolver {
    final Map<String, String> pkgForStruct = new HashMap<String, String>();
    final Map<String, String> pkgForFun = new HashMap<String, String>();
    final Map<String, String> pkgForVar = new HashMap<String, String>();

    final Map<String, TypeStruct> structMap = new HashMap<String, TypeStruct>();
    final Map<String, Function> funMap = new HashMap<String, Function>();
    final Map<String, FieldDecl> varMap = new HashMap<String, FieldDecl>();
    StreamSpec pkg;

    public StreamSpec curPkg() {
        return pkg;
    }
    public Collection<String> structNamesList() {
        return structMap.keySet();
    }

    public NameResolver() {

    }
    public NameResolver(Program p) {
        populate(p);
    }
    public void setPackage(StreamSpec pkg) {
        this.pkg = pkg;
    }

    public String compound(String a, String b) {
        return a + ":" + b;
    }

    private <T> void registerStuff(Map<String, String> pkgForThing,
            Map<String, T> thingMap, T stuff, String name)
    {
        if (pkgForThing.containsKey(name)) {
            pkgForThing.put(name, null);
        } else {
            pkgForThing.put(name, pkg.getName());
        }
        thingMap.put(compound(pkg.getName(), name), stuff);
    }

    public void registerStruct(TypeStruct ts) {
        registerStuff(pkgForStruct, structMap, ts, ts.getName());
    }

    public void registerFun(Function f) {
        registerStuff(pkgForFun, funMap, f, f.getName());
    }

    public void registerVar(FieldDecl fd) {
        for (int i = 0; i < fd.getNumFields(); ++i) {
            registerStuff(pkgForVar, varMap, fd, fd.getName(i));
        }
    }

    public <T> String getFullName(String name, Map<String, String> pkgForThing,
            Map<String, T> chkMap)
    {
        if (name.indexOf(":") > 0) {
            return name;
        }
        if (name.indexOf(":") == 0) {
            name = name.substring(1);
        }
        String pkgName = pkgForThing.get(name);
        if (pkgName == null) {
            String cpkgNm = this.pkg.getName() + ":" + name;
            if (chkMap.containsKey(cpkgNm)) {
                return cpkgNm;
            }
            // System.err.println("Name " + name + " is ambiguous.");
            return null;
        }
        return pkgName + ":" + name;
    }

    public Function getFun(String name) {
        String full = getFullName(name, pkgForFun, funMap);
        if (full == null) {
            return null;
        }
        return funMap.get(full);
    }

    public Function getFun(String name, FENode errSource) {
        Function f = getFun(name);
        if (f == null)
            throw new UnrecognizedVariableException(name, errSource);
        return f;
    }

    public String getFunName(Function f) {
        if (pkgForFun.containsKey(f.getName())) {
            return pkgForFun.get(f.getName()) + ":" + f.getName();
        } else {
            throw new RuntimeException("NYI");
        }
    }

    public String getStructName(String name) {
        return getFullName(name, pkgForStruct, structMap);
    }

    public String getFunName(String name) {
        return getFullName(name, pkgForFun, funMap);
    }

    public TypeStruct getStruct(String name) {
        String full = getFullName(name, pkgForStruct, structMap);
        if (full == null) {
            return null;
        }
        return structMap.get(full);
    }

    public void populate(StreamSpec pkg) {
        this.pkg = pkg;
        for (TypeStruct ts : pkg.getStructs()) {
            registerStruct(ts);
        }
        for (Function f : pkg.getFuncs()) {
            registerFun(f);
        }
        for (FieldDecl fd : pkg.getVars()) {
            registerVar(fd);
        }
    }

    public void populate(Program p) {
        for (StreamSpec pkg : p.getStreams()) {
            populate(pkg);
        }
    }

}