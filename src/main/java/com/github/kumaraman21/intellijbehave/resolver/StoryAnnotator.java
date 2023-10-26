/*
 * Copyright 2011-12 Aman Kumar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.kumaraman21.intellijbehave.resolver;

import com.github.kumaraman21.intellijbehave.parser.JBehaveStep;
import com.github.kumaraman21.intellijbehave.service.JavaStepDefinition;
import com.github.kumaraman21.intellijbehave.utility.ParametrizedString;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;

import static com.github.kumaraman21.intellijbehave.utility.ParametrizedString.StringToken;

public class StoryAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder annotationHolder) {
        if (!(psiElement instanceof JBehaveStep)) {
            return;
        }

        JBehaveStep step = (JBehaveStep) psiElement;
        PsiReference[] references = step.getReferences();

        if (references.length != 1 || !(references[0] instanceof StepPsiReference)) {
            return;
        }

        StepPsiReference reference = (StepPsiReference) references[0];
        JavaStepDefinition definition = reference.resolveToDefinition();
        Boolean[] isLoadStory = new Boolean[1];
        isLoadStory[0] = false;
        PsiElement storyStepDefinition = reference.resolveToDefinitionStory(isLoadStory);

        if (storyStepDefinition != null || isLoadStory[0]) {
            if (storyStepDefinition != null)
                annotateStory(step, storyStepDefinition, annotationHolder);
            else
                annotationHolder.newAnnotation(HighlightSeverity.ERROR, "Load step found, but failed to find unique story to load")
                        .range(psiElement)
                        .create();
        }
        else if (definition == null) {
            annotationHolder.newAnnotation(HighlightSeverity.ERROR, "No definition found for the step")
                    .range(psiElement)
                    .create();
        } else {
            annotateParameters(step, definition, annotationHolder);
        }
    }

    private void annotateParameters(JBehaveStep step, JavaStepDefinition javaStepDefinition, AnnotationHolder annotationHolder) {
        String stepText = step.getStepText();
        String annotationText = javaStepDefinition.getAnnotationTextFor(stepText);
        ParametrizedString pString = new ParametrizedString(annotationText);

        int offset = step.getTextOffset();
        for (StringToken token : pString.tokenize(stepText)) {
            int length = token.getValue().length();
            if (token.isIdentifier()) {
                annotationHolder.newAnnotation(HighlightSeverity.INFORMATION, "Parameter")
                        .range(TextRange.from(offset, length))
                        .create();
            }
            offset += length;
        }
    }

    private void annotateStory(JBehaveStep step, PsiElement storyStepDefinition, AnnotationHolder annotationHolder) {
        int offset = step.getTextOffset();
        int length = step.getStepText().length();
        annotationHolder.newAnnotation(HighlightSeverity.INFORMATION, "Story from file " + storyStepDefinition.getContainingFile().getName())
                //.range(TextRange.from(offset, length))
                .create();
    }
}
