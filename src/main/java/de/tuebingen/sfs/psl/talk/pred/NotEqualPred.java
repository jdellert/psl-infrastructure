/*
 * Copyright 2018–2022 University of Tübingen
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
package de.tuebingen.sfs.psl.talk.pred;

import de.tuebingen.sfs.psl.talk.rule.TalkingPredicate;

public class NotEqualPred extends TalkingPredicate {

    // Internally used to avoid NPEs when generating default explanations for rules.
    // When writing rules use != instead: X != Y.

    public final static String SYMBOL = "#notequal";

    public NotEqualPred() {
        super(SYMBOL, 2);
    }

}
