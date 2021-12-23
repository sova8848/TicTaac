package com.github.rusakovichma.tictaac.mapper;

import com.github.rusakovichma.tictaac.model.threatmodel.annotation.*;
import com.github.rusakovichma.tictaac.parser.model.Node;
import com.github.rusakovichma.tictaac.parser.model.NodeHelper;
import com.github.rusakovichma.tictaac.parser.model.NodeTree;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;

import static com.github.rusakovichma.tictaac.util.ClassUtil.isCollection;
import static com.github.rusakovichma.tictaac.util.ReflectionUtil.*;

abstract class AbstractModelMapper<M> implements ModelMapper<M> {

    private Map<String, LinkedHashMap<String, Object>> rootCollectionRefsCache = new LinkedHashMap<>();
    private LinkedHashMap<String, Object> currentRootCollectionRefs;

    private final NodeTree nodeTree;

    protected AbstractModelMapper(NodeTree nodeTree) {
        this.nodeTree = nodeTree;
    }

    private void initRootCollectionRefsCache(Field rootObjectField) {
        if (rootObjectField.isAnnotationPresent(RootCollection.class)) {
            this.currentRootCollectionRefs = new LinkedHashMap<>();
            this.rootCollectionRefsCache.put(rootObjectField.getName(), this.currentRootCollectionRefs);
        }
    }

    private Object getAndInitRootCollection(Field rootObjectField, Object rootObject)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Object rootCollectionObj = rootObjectField.get(rootObject);
        if (rootCollectionObj == null) {
            rootCollectionObj = rootObjectField.getType().getDeclaredConstructor().newInstance();
            rootObjectField.set(rootObject, rootCollectionObj);
        }
        return rootCollectionObj;
    }

    private void setReferenceForCollectionField(Field fieldOfNewObject, Object newObject, Node propertyNode)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        LinkedHashMap<String, Object> rootCollection = this.rootCollectionRefsCache
                .get(fieldOfNewObject.getAnnotation(Ref.class).rootCollection());
        if (Collection.class.isAssignableFrom(fieldOfNewObject.getType())) {

            Object rootCollectionRefObj = fieldOfNewObject.get(newObject);
            if (rootCollectionRefObj == null) {
                rootCollectionRefObj = fieldOfNewObject.getType().getDeclaredConstructor().newInstance();
                fieldOfNewObject.set(newObject, rootCollectionRefObj);
            }
            Method refAdd = getCollectionAddMethod(rootCollectionRefObj);

            for (Node refNode : propertyNode.getDescendants()) {
                Object refObject = rootCollection.get(refNode.getConventionalName());
                if (refObject != null) {
                    refAdd.invoke(rootCollectionRefObj, refObject);
                }
            }
        }
    }

    private void setReferenceForFlowEntity(Field fieldOfNewObject, Object newObject, Node flowNode, boolean source)
            throws IllegalAccessException {
        LinkedHashMap<String, Object> rootCollection = rootCollectionRefsCache.get("elements");
        Object refObject = rootCollection.get(NodeHelper.getFlowEntity(flowNode.getContent(), source));
        fieldOfNewObject.set(newObject, refObject);
    }

    private void setIdForField(Field fieldOfNewObject, Object newObject, Node element)
            throws IllegalAccessException {
        fieldOfNewObject.set(newObject, element.getConventionalName());
        this.currentRootCollectionRefs.put(element.getConventionalName(), newObject);
    }

    private void setPrimitiveForField(Field fieldOfNewObject, Object newObject, Node propertyNode)
            throws IllegalAccessException {
        Object fieldValue = null;
        if (propertyNode != null) {
            if (Enum.class.isAssignableFrom(fieldOfNewObject.getType())) {
                fieldValue = createEnumInstance(propertyNode.getConventionalNodeValue(), fieldOfNewObject.getType());
            } else if (String.class.isAssignableFrom(fieldOfNewObject.getType())) {
                fieldValue = propertyNode.getNodeValue();
            }
        }
        fieldOfNewObject.set(newObject, fieldValue);
    }

    private void processFieldsOfRootCollectionObject(Object newRootCollectionObject, Node rootCollectionElementNode)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException, InstantiationException {
        for (Field fieldOfNewObject : newRootCollectionObject.getClass().getDeclaredFields()) {
            fieldOfNewObject.setAccessible(true);

            if (fieldOfNewObject.isAnnotationPresent(Exclude.class)) {
                continue;
            }

            if (fieldOfNewObject.isAnnotationPresent(Id.class)) {
                setIdForField(fieldOfNewObject, newRootCollectionObject, rootCollectionElementNode);
                continue;
            }

            if (fieldOfNewObject.isAnnotationPresent(FlowSource.class)) {
                setReferenceForFlowEntity(fieldOfNewObject, newRootCollectionObject, rootCollectionElementNode, true);
                continue;
            }

            if (fieldOfNewObject.isAnnotationPresent(FlowTarget.class)) {
                setReferenceForFlowEntity(fieldOfNewObject, newRootCollectionObject, rootCollectionElementNode, false);
                continue;
            }

            Node propertyNode = rootCollectionElementNode.getDescendants()
                    .filter(node -> fieldOfNewObject.getName().equals(node.getConventionalName()))
                    .orElse(null);

            if (fieldOfNewObject.isAnnotationPresent(Ref.class)) {
                if (propertyNode == null) {
                    continue;
                }

                setReferenceForCollectionField(fieldOfNewObject, newRootCollectionObject, propertyNode);
                continue;
            }

            setPrimitiveForField(fieldOfNewObject, newRootCollectionObject, propertyNode);
        }
    }

    protected void mapModel(M rootObject) {
        try {
            for (Field rootField : rootObject.getClass().getDeclaredFields()) {
                rootField.setAccessible(true);

                Node rootCollectionNode = nodeTree.filterNode(new ArrayList<>(
                        Arrays.asList(
                                node -> node.getNodeLevel() == 0,
                                node -> rootField.getName().equals(node.getConventionalName())
                        )
                )).orElse(null);

                if (isCollection(rootField.getType())) {

                    initRootCollectionRefsCache(rootField);

                    Object rootCollection = getAndInitRootCollection(rootField, rootObject);
                    Method rootCollectionAdd = getCollectionAddMethod(rootCollection);

                    Type parameterType = getCollectionParameterType(rootField);

                    NodeTree rootCollectionElementNodes = rootCollectionNode.getDescendants();

                    for (Node rootCollectionElementNode : rootCollectionElementNodes) {

                        Object newRootCollectionObject = Class.forName(parameterType.getTypeName())
                                .getDeclaredConstructor().newInstance();
                        rootCollectionAdd.invoke(rootCollection, newRootCollectionObject);

                        processFieldsOfRootCollectionObject(newRootCollectionObject, rootCollectionElementNode);
                    }
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

}