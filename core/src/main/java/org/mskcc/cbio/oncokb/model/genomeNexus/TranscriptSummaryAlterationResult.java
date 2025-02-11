package org.mskcc.cbio.oncokb.model.genomeNexus;

import org.genome_nexus.client.TranscriptConsequenceSummary;
import org.mskcc.cbio.oncokb.model.Alteration;

/**
 * This class serves as a structured way to communicate the relationship between 
 * a genomic variant's transcript consequence summary from GN and its corresponding alteration in OncoKB.
 * It helps maintain traceability of how an alteration was derived.
 */
public class TranscriptSummaryAlterationResult {
    private TranscriptConsequenceSummary transcriptConsequenceSummary;  // the summary where transcriptId matches an oncokb canonical transcript
    private Alteration alteration = new Alteration();  // the Alteration model generated using the transcript summary; empty alteration by default
    private String message; // optional message for this annotation


    public TranscriptSummaryAlterationResult() { }

    public TranscriptSummaryAlterationResult(TranscriptConsequenceSummary transcriptConsequenceSummary) {
        this.transcriptConsequenceSummary = transcriptConsequenceSummary;
    }


    public TranscriptSummaryAlterationResult(TranscriptConsequenceSummary transcriptConsequenceSummary, Alteration alteration) {
        this.transcriptConsequenceSummary = transcriptConsequenceSummary;
        this.alteration = alteration;
    }

    public TranscriptConsequenceSummary getTranscriptConsequenceSummary() {
        return this.transcriptConsequenceSummary;
    }

    public void setTranscriptConsequenceSummary(TranscriptConsequenceSummary transcriptConsequenceSummary) {
        this.transcriptConsequenceSummary = transcriptConsequenceSummary;
    }

    public Alteration getAlteration() {
        return this.alteration;
    }

    public void setAlteration(Alteration alteration) {
        this.alteration = alteration;
    }

    public String getMessage() {
        return this.message;
    }

    public void setMessage(String message) {
        this.message = message;
    }


}
