module de.tuebingen.sfs.psl {
    requires psl.core;
    requires java.desktop;
    requires psl.groovy;
    requires psl.parser;
    requires h2;
    requires java.sql;
    requires com.fasterxml.jackson.databind;
    exports de.tuebingen.sfs.psl.engine;
    opens de.tuebingen.sfs.psl.engine;
    exports de.tuebingen.sfs.psl.eval;
    exports de.tuebingen.sfs.psl.io;
    exports de.tuebingen.sfs.psl.talk;
    exports de.tuebingen.sfs.psl.util.color;
    exports de.tuebingen.sfs.psl.util.data;
    exports de.tuebingen.sfs.psl.util.log;
}
