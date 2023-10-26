package com.github.kumaraman21.intellijbehave.service;

import com.github.kumaraman21.intellijbehave.kotlin.KotlinConfigKt;
import com.github.kumaraman21.intellijbehave.kotlin.support.services.KotlinAnnotationsLoader;
import com.github.kumaraman21.intellijbehave.parser.JBehaveStep;
import com.github.kumaraman21.intellijbehave.parser.StoryFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.impl.java.stubs.index.JavaFullClassNameIndex;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ReflectionUtil;
import org.jbehave.core.annotations.Given;
import org.jbehave.core.annotations.Then;
import org.jbehave.core.annotations.When;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

/**
 * Project service that provides Java step definitions for JBehave Story steps.
 */
@Service(Service.Level.PROJECT)
public final class JBehaveStepsIndex {
    public JBehaveStepsIndex(Project project) {
    }

    public static JBehaveStepsIndex getInstance(Project project) {
        return project.getService(JBehaveStepsIndex.class);
    }

    @NotNull
    public Collection<JavaStepDefinition> findStepDefinitions(@NotNull JBehaveStep step) {
        Module module = ModuleUtilCore.findModuleForPsiElement(step);

        if (module == null) {
            return emptyList();
        }

        Map<Class, JavaStepDefinition> definitionsByClass = new HashMap<>();
        List<JavaStepDefinition> stepDefinitions = loadStepsFor(module);
        String stepText = step.getStepText();

        for (JavaStepDefinition stepDefinition : stepDefinitions) {
            if (stepDefinition.matches(stepText) && stepDefinition.supportsStep(step)) {
                Integer currentHighestPriority = getPriorityByDefinition(definitionsByClass.get(stepDefinition.getClass()));
                Integer newPriority = getPriorityByDefinition(stepDefinition);

                if (newPriority > currentHighestPriority) {
                    definitionsByClass.put(stepDefinition.getClass(), stepDefinition);
                }
            }
        }

        return definitionsByClass.values();
    }

    public PsiElement findStepDefinitionsInStory(@NotNull JBehaveStep step) {
        try {
            Module module = ModuleUtilCore.findModuleForPsiElement(step);
            GlobalSearchScope searchScope = module.getModuleWithDependenciesAndLibrariesScope(true);

            String stepText = step.getStepText();
            PsiElement story = null;
            if (stepText.contains("��������� �������")) {
                String[] textSplited = stepText.split("/");
                String storyName = textSplited[textSplited.length - 1];
                textSplited = null;
                if (storyName.contains(" � Examples"))
                    textSplited = storyName.split(" � Examples");
                else if (storyName.contains(" � examples"))
                    textSplited = storyName.split(" � examples");
                if (textSplited != null)
                    storyName = textSplited[0];
                if (!storyName.contains(".story"))
                    storyName += ".story";
                Project project = module.getProject();
                PsiFile[] psiFiles = FilenameIndex.getFilesByName(project, storyName, searchScope);
                if (psiFiles.length != 1)
                    return null;
                story = ((StoryFile) psiFiles[0]).getStory();
            } else if (stepText.contains("������� ->") || stepText.contains("������� ->")) {
                String[] textSplited = stepText.split("->");
                String storyName = textSplited[textSplited.length - 1].trim();
                textSplited = null;
                if (storyName.contains(" � Examples"))
                    textSplited = storyName.split(" � Examples");
                else if (storyName.contains(" � examples"))
                    textSplited = storyName.split(" � examples");
                if (textSplited != null)
                    storyName = textSplited[0];
                if (!storyName.contains(".story"))
                    storyName += ".story";
                Project project = module.getProject();
                PsiFile[] psiFiles = FilenameIndex.getFilesByName(project, storyName, searchScope);
                if (psiFiles.length != 1)
                    return null;
                story = ((StoryFile) psiFiles[0]).getStory();
            }
            return story;
        } catch (Exception e) {
            return null;
        }
    }

    @NotNull
    private List<JavaStepDefinition> loadStepsFor(@NotNull Module module) {
        GlobalSearchScope dependenciesScope = module.getModuleWithDependenciesAndLibrariesScope(true);

        PsiClass givenAnnotationClass = findStepAnnotation(Given.class.getName(), module, dependenciesScope);
        PsiClass whenAnnotationClass = findStepAnnotation(When.class.getName(), module, dependenciesScope);
        PsiClass thenAnnotationClass = findStepAnnotation(Then.class.getName(), module, dependenciesScope);

        if (givenAnnotationClass == null || whenAnnotationClass == null || thenAnnotationClass == null) {
            return emptyList();
        }

        List<JavaStepDefinition> result = new ArrayList<>();

        List<PsiClass> stepAnnotations = asList(givenAnnotationClass, whenAnnotationClass, thenAnnotationClass);
        for (PsiClass stepAnnotation : stepAnnotations) {
            for (PsiAnnotation stepDefAnnotation : getAllStepAnnotations(stepAnnotation, dependenciesScope)) {
                result.add(new JavaStepDefinition(stepDefAnnotation));
            }
        }

        return result;
    }

    @NotNull
    private static Integer getPriorityByDefinition(@Nullable JavaStepDefinition definition) {
        return definition != null ? definition.getAnnotationPriority() : -1;
    }

    @NotNull
    private static Collection<PsiAnnotation> getAllStepAnnotations(@NotNull final PsiClass annClass, @NotNull final GlobalSearchScope scope) {
        return ApplicationManager.getApplication().runReadAction((Computable<Collection<PsiAnnotation>>) () -> {
            Project project = annClass.getProject();
            Collection<PsiAnnotation> psiAnnotations = new ArrayList<>();
            if (KotlinConfigKt.getPluginIsEnabled()) {
                String qualifiedName = annClass.getQualifiedName();
                if (qualifiedName != null) {
                    psiAnnotations.addAll(KotlinAnnotationsLoader.getInstance().getAnnotations(QualifiedName.fromDottedString(qualifiedName), project, scope));
                }
            }
            psiAnnotations.addAll(JavaAnnotationIndex.getInstance().get(annClass.getName(), project, scope));
            return psiAnnotations;
        });
    }

    @Nullable
    private PsiClass findStepAnnotation(String stepClass, Module module, GlobalSearchScope dependenciesScope) {
        Method getPre20221 = ReflectionUtil.getDeclaredMethod(JavaFullClassNameIndex.class, "get", Integer.class, Project.class, GlobalSearchScope.class);
        Method get20221 = ReflectionUtil.getDeclaredMethod(JavaFullClassNameIndex.class, "get", CharSequence.class, Project.class, GlobalSearchScope.class);

        try {
            Object javaFullClassNameIndexInstance = JavaFullClassNameIndex.class.getDeclaredMethod("getInstance").invoke(JavaFullClassNameIndex.class);
            Collection<PsiClass> stepDefAnnotationCandidates = Collections.emptyList();
            if (getPre20221 != null) {
                stepDefAnnotationCandidates = (Collection<PsiClass>) getPre20221.invoke(javaFullClassNameIndexInstance, stepClass.hashCode(), module.getProject(), dependenciesScope);
            } else if (get20221 != null) {
                stepDefAnnotationCandidates = (Collection<PsiClass>) get20221.invoke(javaFullClassNameIndexInstance, stepClass, module.getProject(), dependenciesScope);
            }
            for (PsiClass stepDefAnnotations : stepDefAnnotationCandidates) {
                if (stepClass.equals(stepDefAnnotations.getQualifiedName())) {
                    return stepDefAnnotations;
                }
            }
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            //fall through and return null
        }
        return null;
    }
}
