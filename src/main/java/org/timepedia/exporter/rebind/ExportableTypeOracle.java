package org.timepedia.exporter.rebind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.timepedia.exporter.client.Export;
import org.timepedia.exporter.client.ExportClosure;
import org.timepedia.exporter.client.NoExport;
import org.timepedia.exporter.client.StructuralType;

import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.typeinfo.JAbstractMethod;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JConstructor;
import com.google.gwt.core.ext.typeinfo.JField;
import com.google.gwt.core.ext.typeinfo.JMethod;
import com.google.gwt.core.ext.typeinfo.JType;
import com.google.gwt.core.ext.typeinfo.TypeOracle;
import com.google.gwt.core.ext.typeinfo.TypeOracleException;

/**
 *
 */
public class ExportableTypeOracle {

  public static final String JSO_CLASS
      = "com.google.gwt.core.client.JavaScriptObject";

  static final String EXPORTER_CLASS = "org.timepedia.exporter.client.Exporter";

  static final String EXPORTABLE_CLASS
      = "org.timepedia.exporter.client.Exportable";

  static final String EXPORTALL_CLASS = "org.timepedia.exporter.client.ExporterUtil.ExportAll";

  static final String EXPORT_OVERLAY_CLASS
      = "org.timepedia.exporter.client.ExportOverlay";

  private static final String STRING_CLASS = "java.lang.String";

  private JClassType exportAllType;

  public boolean isExportable(JField field) {
    return field.isStatic() && field.isPublic() && field.isFinal() && (
        isExportable(field.getAnnotation(Export.class)) || (
            isExportable(field.getEnclosingType()) && !isNotExportable(
                field.getAnnotation(NoExport.class))));
  }

  public boolean isExportable(JClassType type) {
    return isExportable(type.getAnnotation(Export.class)) || 
        (type.isInterface() != null && 
        isExportable(type.getAnnotation(ExportClosure.class)));
  }

  public static <T> boolean isExportable(Export annotation) {
    return annotation != null;
  }
  
  public JExportableMethod isExportable(JAbstractMethod method, JExportableClassType type) {
    boolean export = false;
    
    // Only public methods are exported
    if (method.isPublic()) {
      Export e;
      if (method instanceof JConstructor && method.getParameters().length == 0) {
        // zero-arg constructors always exportable
        export =  true;
      } else if (isNotExportable(method.getAnnotation(NoExport.class))) {
        // Do not export methods annotated as NoExport, although the 
        // method is marked as export in an interface or the entire class
        // is annotated as Export
        export = false;
      } else if ((e = type.getType().getAnnotation(Export.class)) != null && e.all()) {
        // Export this method if the class has the Export.all attribute set
        export = true;
      } else if (isExportable(method.getAnnotation(Export.class))) {
        // Export this method if has the Export annotation
        export = true;
      } else if (isExportable(method.getEnclosingType())) {
        // Export all method in a class annotated as Export
        export = true;
      } else {
        // Export methods which are annotated in implemented interfaces
        for (JClassType c : method.getEnclosingType().getImplementedInterfaces()) {
          for (JMethod m : c.getMethods()) {
            if (!isNotExportable(m.getAnnotation(NoExport.class))
                && (isExportable(c) || isExportable(m.getAnnotation(Export.class)))
                && m.getName().equals(method.getName())) {
              if (m.getReadableDeclaration(true, true, false, true, true).equals(
                  ((JMethod) method).getReadableDeclaration(true, true, false,
                      true, true))) {
                export = true;
                break;
              }
            }
          }
        }
      }
    }
    
    JExportableMethod m = null;
    if (export) {
      if (method instanceof JConstructor) {
        m = new JExportableConstructor(type, method);
      } else {
        m = new JExportableMethod(type, method);
        
        // 
        // Time ago, return type needed to be exported if it was not a primitive
        // String,Number,JSO, etc and it hasn't already been exported
        // we needed to export it because we needed it to wrap the returned value
        // 
        // Now we do not need check return type or parameters although
        // we could could filter methods here
        
        /*
        if (m.getExportableReturnType() == null) {
          return null;
        }
        for (JExportableParameter p : m.getExportableParameters()) {
          if (!p.isExportable()) {
            return null;
          }
        } */
        
      }
    }
    return m;
  }

  private static boolean isExportable(ExportClosure annotation) {
    return annotation != null;
  }

  private static boolean isNotExportable(NoExport annotation) {
    return annotation != null;
  }

  private TypeOracle typeOracle;

  private TreeLogger log;

  private JClassType exportableType = null;

  private JClassType jsoType = null;

  private JClassType stringType = null;

  private JClassType exportOverlayType;

  private Map<String, JExportOverlayClassType> overlayTypes
      = new HashMap<String, JExportOverlayClassType>();

  public ExportableTypeOracle(TypeOracle typeOracle, TreeLogger log) {
    this.typeOracle = typeOracle;
    this.log = log;
    exportableType = typeOracle.findType(EXPORTABLE_CLASS);
    exportOverlayType = typeOracle.findType(EXPORT_OVERLAY_CLASS);
    exportAllType = typeOracle.findType(EXPORTALL_CLASS);

    jsoType = typeOracle.findType(JSO_CLASS);
    stringType = typeOracle.findType(STRING_CLASS);
    assert exportableType != null;
    assert exportOverlayType != null;
    assert jsoType != null;
    assert stringType != null;

    for (JClassType t : typeOracle.getTypes()) {
      if (t.isAssignableTo(exportOverlayType) && !t.equals(exportOverlayType)) {
        JClassType targetType = getExportOverlayType(t);
        overlayTypes.put(targetType.getQualifiedSourceName(),
            new JExportOverlayClassType(this, t));
      }
    }
  }

  public JExportableClassType findExportableClassType(String requestedClass) {
    JClassType requestedType = typeOracle.findType(requestedClass);
    if (requestedType != null) {
      if (requestedType.isAssignableTo(exportOverlayType)) {
        return new JExportOverlayClassType(this, requestedType);
      } else if (requestedType.isAssignableTo(exportableType)) {
        return new JExportableClassType(this, requestedType);
      }
      JExportOverlayClassType exportOverlay = overlayTypes.get(requestedClass);
      return exportOverlay;
    }
    return null;
  }

  public JExportableType findExportableType(String paramTypeName) {
    try {
      JType type = typeOracle.parse(paramTypeName);
      JClassType cType = type != null ? type.isClassOrInterface() : null;
      if (type.isPrimitive() != null) {
        return new JExportablePrimitiveType(this, type.isPrimitive());
      } else if (type.isArray() != null) {
        return new JExportableArrayType(this, type.isArray());
      } else if (overlayTypes.containsKey(paramTypeName)) {
        return overlayTypes.get(paramTypeName);
      } else if (cType.isAssignableTo(exportOverlayType)) {
        return new JExportOverlayClassType(this, type.isClassOrInterface());
      } else if (cType != null && (cType.isAssignableTo(exportableType)
          || cType.isAssignableTo(stringType) || cType
          .isAssignableTo(jsoType))) {
        return new JExportableClassType(this, type.isClassOrInterface());
      } else {
        return null;
      }
    } catch (TypeOracleException e) {
      return null;
    }
  }

  public JClassType getExportOverlayType(JClassType requestedType) {
    JClassType[] inf = requestedType.getImplementedInterfaces();
    for (JClassType i : inf) {
      if (isExportOverlay(i)) {
        return i.isParameterized().getTypeArgs()[0];
      }
    }
    return null;
  }

  public boolean isArray(JExportableClassType jExportableClassType) {
    return jExportableClassType.getType().isArray() != null;
  }

  public boolean isClosure(String qualifiedSourceName) {
    if (qualifiedSourceName == null) {
      return false;
    }
    JType type = typeOracle.findType(qualifiedSourceName);
    if (type == null) {
      return false;
    }
    JClassType cType = type.isClassOrInterface();

    if (cType != null && cType.isAssignableTo(exportableType)) {
      ExportClosure ann = cType.getAnnotation(ExportClosure.class);
      if (ann != null && cType.isInterface() != null) {

        return cType.getMethods().length == 1;
      }
    }
    return false;
  }

  public boolean isExportOverlay(JClassType i) {
    return i.isAssignableTo(exportOverlayType);
  }

  public boolean isJavaScriptObject(JExportableClassType type) {
    return type.getType().isAssignableTo(jsoType);
  }

  public boolean isString(JExportableClassType type) {
    return type.getType().isAssignableTo(stringType);
  }

  public boolean isString(JType type) {
    return type.isClass() != null && type.isClass().isAssignableTo(stringType);
  }

  public boolean isJavaScriptObject(JType type) {
    return type.isClass() != null && type.isClass().isAssignableTo(jsoType);
  }

  public boolean isExportAll(String requestedClass) {
    return typeOracle.findType(requestedClass).isAssignableTo(exportAllType);
  }

  public List<JClassType> findAllExportableTypes() {
    ArrayList<JClassType> types = new ArrayList<JClassType>();
    for (JClassType t : typeOracle.getTypes()) {
      if (t.equals(exportAllType) || t.equals(exportableType) || t
          .equals(exportOverlayType)) {
        continue;
      }
      if (t.isAssignableTo(exportableType)
          || t.isAssignableTo(exportOverlayType)) {
        if (t.isDefaultInstantiable()
            && t.isPublic()
            && new JExportableClassType(this, t).getExportableMethods().length > 0) {
          types.add(t);
        }
      }
    }
    return types;
  }

  public boolean isStructuralType(JClassType type) {
    // always false for now until enabled
    return false && type.getAnnotation(StructuralType.class) != null;
  }

  public String getJsTypeOf(JClassType type) {
    if (type.isAssignableTo(stringType)) {
      return "string";
    } else if (type.isAssignableTo(jsoType)) {
      return "object";
    }
    return "@" + type.getQualifiedSourceName() + "::class";
  }
}
