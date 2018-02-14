import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import uk.ac.cam.ch.wwmm.oscar.Oscar;
import uk.ac.cam.ch.wwmm.oscar.chemnamedict.entities.ResolvedNamedEntity;
import uk.ac.cam.ch.wwmm.oscar.document.NamedEntity;
import uk.ac.cam.ch.wwmm.oscar.document.TokenSequence;
import uk.ac.cam.ch.wwmm.oscar.types.NamedEntityType;
import uk.ac.cam.ch.wwmm.oscarMEMM.MEMMRecogniser;
import uk.ac.cam.ch.wwmm.oscarpattern.PatternRecogniser;
import gate.*;
import gate.creole.*;
import gate.util.*;
import gate.util.persistence.PersistenceManager;
import gate.corpora.RepositioningInfo;

public class OscarGateAnnotator {
    /** The Corpus Pipeline application to contain ANNIE */
    private CorpusController annieController;

    /**
     * Initialise the ANNIE system. This creates a "corpus pipeline"
     * application that can be used to run sets of documents through
     * the extraction system.
     */
    public void initAnnie() throws GateException, IOException {
        Out.prln("Initialising ANNIE...");

        // load the ANNIE application from the saved state in plugins/ANNIE
        File pluginsHome = Gate.getPluginsHome();
        File anniePlugin = new File(pluginsHome, "ANNIE");
        File annieGapp = new File(anniePlugin, "ANNIE_with_defaults.gapp");
        annieController =
                (CorpusController) PersistenceManager.loadObjectFromFile(annieGapp);

        Out.prln("...ANNIE loaded");
    } // initAnnie()

    /** Tell ANNIE's controller about the corpus you want to run on */
    public void setCorpus(Corpus corpus) {
        annieController.setCorpus(corpus);
    } // setCorpus

    /** Run ANNIE */
    public void execute() throws GateException {
        Out.prln("Running ANNIE...");
        annieController.execute();
        Out.prln("...ANNIE complete");
    } // execute()

    public static void main(String[] args) throws GateException, IOException {
        OscarGateAnnotator oa = new OscarGateAnnotator();
        oa.run(args);
    }

    public void run(String[] args) throws GateException, IOException {
        if(Gate.getGateHome() == null)
            Gate.setGateHome(new File("/Applications/GATE_Developer_8.2"));
        if(Gate.getPluginsHome() == null)
            Gate.setPluginsHome(new File("/Applications/GATE_Developer_8.2/plugins"));
        Out.prln("Initialising GATE...");
        Gate.init();
        Out.prln("...GATE initialised");
        this.initAnnie();
        Oscar oscar = new Oscar();
        PatternRecogniser patternrecogniser = new PatternRecogniser();
        //patternrecogniser.setDeprioritiseOnts(true);
        MEMMRecogniser memmRecogniser = new MEMMRecogniser();
        Out.prln("Initialized OSCAR sucessfully...");

        Path dirPath = Paths.get("./_test");


        List <File> files = Files.walk(dirPath)
                .filter(Files::isRegularFile)
                .map(Path::toFile)
                .collect(Collectors.toList());
        Corpus corpus = Factory.newCorpus("StandAloneAnnie corpus");
        for(int i = 0; i < files.size(); i++) {

            URL u = new URL("file://"+files.get(i).getAbsolutePath());
            FeatureMap params = Factory.newFeatureMap();
            params.put("sourceUrl", u);
            params.put("preserveOriginalContent", new Boolean(true));
            params.put("collectRepositioningInfo", new Boolean(true));
            Out.prln("Creating doc for " + u);
            Document doc = (Document)
                    Factory.createResource("gate.corpora.DocumentImpl", params);
            corpus.add(doc);
        } // for each of args

        setCorpus(corpus);
        execute();

        // for each document, get an XML document with the
        // person and location names added
        Iterator iter = corpus.iterator();

        int count = 0;

        while(iter.hasNext()) {
            Document doc = (Document) iter.next();
            Out.prln("real doc created");
            String sourceFileName = doc.getName();
            String TextDoc = doc.getContent().toString();
            //String Text = xml_parser.parse(TextDoc);
            //Out.prln("text imported form xml");
            AnnotationSet defaultAnnotSet = doc.getAnnotations();
            Set annotTypesRequired = new HashSet();
            annotTypesRequired.add("Person");
            annotTypesRequired.add("Location");
            Set<Annotation> peopleAndPlaces =
                    new HashSet<Annotation>(defaultAnnotSet.get(annotTypesRequired));

            FeatureMap features = doc.getFeatures();
            String originalContent = (String)
                    features.get(GateConstants.ORIGINAL_DOCUMENT_CONTENT_FEATURE_NAME);
            RepositioningInfo info = (RepositioningInfo)
                    features.get(GateConstants.DOCUMENT_REPOSITIONING_INFO_FEATURE_NAME);

            ++count;
            File file = new File("StANNIE_" + count + ".HTML");
            Out.prln("File name: '"+file.getAbsolutePath()+"'");
            //+ Here we call OSCAR for the same file
            List<TokenSequence> tokSeqs = oscar.tokenise(TextDoc);
            List<NamedEntity> entitiesP = patternrecogniser.findNamedEntities(tokSeqs);
            List<NamedEntity> entitiesM = memmRecogniser.findNamedEntities(tokSeqs);
            List<ResolvedNamedEntity> entities = oscar.findAndResolveNamedEntities(TextDoc);
            for (ResolvedNamedEntity ne : entities) {
                FeatureMap fm = Factory.newFeatureMap();
                defaultAnnotSet.add((long)ne.getStart(), (long)ne.getEnd(), ne.getType().toString(), fm);

            }

            if(info != null) {
                Out.prln("OrigContent and reposInfo existing. Generate file...");

                Iterator it = peopleAndPlaces.iterator();
                Annotation currAnnot;
                SortedAnnotationList sortedAnnotations = new SortedAnnotationList();

                //+ This code fills annotations made by ANNIE. We actually do not need it, just for testing
                while(it.hasNext()) {
                    currAnnot = (Annotation) it.next();
                    sortedAnnotations.addSortedExclusive(currAnnot);
                } // while


                StringBuffer editableContent = new StringBuffer(originalContent);
                long insertPositionEnd;
                long insertPositionStart;
                // insert anotation tags backward
                Out.prln("Unsorted annotations count: "+peopleAndPlaces.size());
                Out.prln("Sorted annotations count: "+sortedAnnotations.size());
                for(int i=sortedAnnotations.size()-1; i>=0; --i) {
                    currAnnot = (Annotation) sortedAnnotations.get(i);
                    insertPositionStart =
                            currAnnot.getStartNode().getOffset().longValue();
                    insertPositionStart = info.getOriginalPos(insertPositionStart);
                    insertPositionEnd = currAnnot.getEndNode().getOffset().longValue();
                    insertPositionEnd = info.getOriginalPos(insertPositionEnd, true);
                    //currAnnot.getType());
                    //currAnnot.getId().toString());
                } // if
            } // for

            String xmlDocument = doc.toXml();//defaultAnnotSet, false);
            String fileName = new String("resGateAnnot/"+sourceFileName+ "_processed" +count + ".xml");
            FileWriter writer = new FileWriter(fileName);
            writer.write(xmlDocument);
            writer.close();

        } // for each doc

    }   //± main

    public static class SortedAnnotationList extends Vector {
        public SortedAnnotationList() {
            super();
        } // SortedAnnotationList

        public boolean addSortedExclusive(Annotation annot) {
            Annotation currAnot = null;

            // overlapping check
            for (int i=0; i<size(); ++i) {
                currAnot = (Annotation) get(i);
                if(annot.overlaps(currAnot)) {
                    return false;
                } // if
            } // for

            long annotStart = annot.getStartNode().getOffset().longValue();
            long currStart;
            // insert
            for (int i=0; i < size(); ++i) {
                currAnot = (Annotation) get(i);
                currStart = currAnot.getStartNode().getOffset().longValue();
                if(annotStart < currStart) {
                    insertElementAt(annot, i);
              /*
               Out.prln("Insert start: "+annotStart+" at position: "+i+" size="+size());
               Out.prln("Current start: "+currStart);
               */
                    return true;
                } // if
            } // for

            int size = size();
            insertElementAt(annot, size);
    //Out.prln("Insert start: "+annotStart+" at size position: "+size);
            return true;
        } // addSorted
    } // SortedAnnotationList

}
