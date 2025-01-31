package org.mskcc.cbio.oncokb.model;


import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import javax.persistence.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author jgao
 */

@NamedQueries({
    @NamedQuery(
        name = "findAlterationsByGene",
        query = "select a from Alteration a where a.gene=?"
    ),
    @NamedQuery(
        name = "findAlteration",
        query = "select a from Alteration a where a.gene=? and a.alteration=?"
    ),
    @NamedQuery(
        name = "findAlterationAndReferenceGenome",
        query = "select a from Alteration a join a.referenceGenomes r where a.gene=? and a.alteration=? and r=?"
    ),
    @NamedQuery(
        name = "findAlterationByAlterationAndName",
        query = "select a from Alteration a where a.gene=? and a.alteration=? and a.name=?"
    ),
    @NamedQuery(
        name = "findAlterationByAlterationAndNameAndReferenceGenome",
        query = "select a from Alteration a join a.referenceGenomes r where a.gene=? and a.alteration=? and a.name=? and r=?"
    ),
    @NamedQuery(
        name = "findMutationsByConsequence",
        query = "select a from Alteration a where a.gene=? and a.consequence=?"
    ),
    @NamedQuery(
        name = "findMutationsByConsequenceAndPosition",
        query = "select a from Alteration a where a.gene=? and a.consequence=? and a.proteinStart<=? and a.proteinEnd>=?"
    ),
    @NamedQuery(
        name = "findMutationsByConsequenceAndPositionOnSamePosition",
        query = "select a from Alteration a where a.gene=? and a.consequence=? and a.proteinStart>=? and a.proteinStart<=? and a.proteinStart=a.proteinEnd"
    )
})

@Entity
@Table(name = "alteration")
public class Alteration implements java.io.Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Integer id;

    @Column(length = 40)
    @JsonIgnore
    private String uuid;

    @ManyToOne(fetch = FetchType.EAGER)
    @Fetch(FetchMode.JOIN)
    @JoinColumn(name = "entrez_gene_id")
    private Gene gene;

    @Column(name = "alteration_type")
    @Enumerated(EnumType.STRING)
    @JsonIgnore
    private AlterationType alterationType;

    @ManyToOne(fetch = FetchType.EAGER)
    @Fetch(FetchMode.JOIN)
    @JoinColumn(name = "consequence")
    private VariantConsequence consequence;

    @Lob
    private String alteration;

    @Lob
    @Column(name = "protein_change")
    private String proteinChange;

    @Lob
    private String name;

    @Column(name = "ref_residues")
    private String refResidues;

    @Column(name = "protein_start")
    private Integer proteinStart;

    @Column(name = "protein_end")
    private Integer proteinEnd;

    @Column(name = "variant_residues")
    private String variantResidues;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "alteration_reference_genome", joinColumns = @JoinColumn(name = "alteration_id", nullable = false))
    @Column(length = 10, name = "reference_genome")
    @Enumerated(EnumType.STRING)
    private Set<ReferenceGenome> referenceGenomes = new HashSet<>(0);

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "portal_alteration_oncokb_alteration", joinColumns = {
        @JoinColumn(name = "oncokb_alteration_id", nullable = false, updatable = false)},
        inverseJoinColumns = {@JoinColumn(name = "portal_alteration_id",
            nullable = false, updatable = false)})
    @JsonIgnore
    private Set<PortalAlteration> portalAlterations = new HashSet<PortalAlteration>(0);

    public Set<PortalAlteration> getPortalAlterations() {
        return portalAlterations;
    }

    public void setPortalAlterations(Set<PortalAlteration> portalAlterations) {
        this.portalAlterations = portalAlterations;
    }

    public Alteration() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public Gene getGene() {
        return this.gene;
    }


    public void setGene(Gene gene) {
        this.gene = gene;
    }

    public String getAlteration() {
        return this.alteration;
    }


    public void setAlteration(String alteration) {
        this.alteration = alteration;
    }

    public String getProteinChange() {
        return proteinChange;
    }

    public void setProteinChange(String proteinChange) {
        this.proteinChange = proteinChange;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AlterationType getAlterationType() {
        return this.alterationType;
    }


    public void setAlterationType(AlterationType alterationType) {
        this.alterationType = alterationType;
    }

    public VariantConsequence getConsequence() {
        return consequence;
    }

    public void setConsequence(VariantConsequence consequence) {
        this.consequence = consequence;
    }

    public String getRefResidues() {
        return refResidues;
    }

    public void setRefResidues(String refResidues) {
        this.refResidues = refResidues;
    }

    public Integer getProteinStart() {
        return proteinStart;
    }

    public void setProteinStart(Integer proteinStart) {
        this.proteinStart = proteinStart;
    }

    public Integer getProteinEnd() {
        return proteinEnd;
    }

    public void setProteinEnd(Integer proteinEnd) {
        this.proteinEnd = proteinEnd;
    }

    public String getVariantResidues() {
        return variantResidues;
    }

    public void setVariantResidues(String variantResidues) {
        this.variantResidues = variantResidues;
    }

    public Set<ReferenceGenome> getReferenceGenomes() {
        return referenceGenomes;
    }

    public void setReferenceGenomes(Set<ReferenceGenome> referenceGenomes) {
        this.referenceGenomes = referenceGenomes;
    }

    @Override
    public int hashCode() {
        if (getId() != null) {
            return getId();
        } else {
            return Objects.hash(this.gene, this.alteration, this.referenceGenomes);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Alteration)) return false;
        Alteration that = (Alteration) o;
        if (getId() != null && that.getId() != null) {
            return Objects.equals(getId(), that.getId());
        }
        return Objects.equals(getGene(), that.getGene()) &&
            getAlterationType() == that.getAlterationType() &&
            Objects.equals(getConsequence(), that.getConsequence()) &&
            Objects.equals(getAlteration(), that.getAlteration()) &&
            Objects.equals(getProteinChange(), that.getProteinChange()) &&
            Objects.equals(getName(), that.getName()) &&
            Objects.equals(getRefResidues(), that.getRefResidues()) &&
            Objects.equals(getProteinStart(), that.getProteinStart()) &&
            Objects.equals(getProteinEnd(), that.getProteinEnd()) &&
            Objects.equals(getVariantResidues(), that.getVariantResidues()) &&
            Objects.equals(getReferenceGenomes(), that.getReferenceGenomes());
    }

    @Override
    public String toString() {
        return gene + " " + alteration;
    }

    @JsonIgnore
    public String getUniqueId() {
        List<String> content = new ArrayList<>();
        if (this.gene != null) {
            if (this.gene.getHugoSymbol() != null) {
                content.add(this.gene.getHugoSymbol());
            } else {
                content.add("");
            }
        }
        if (this.alteration != null) {
            content.add(this.alteration);
        } else {
            content.add("");
        }
        if (this.proteinChange != null) {
            content.add(this.proteinChange);
        } else {
            content.add("");
        }
        if (this.name != null) {
            content.add(this.name);
        } else {
            content.add("");
        }
        if (this.alterationType != null) {
            content.add(this.alterationType.name());
        } else {
            content.add("");
        }
        if (this.consequence != null) {
            content.add(this.consequence.getTerm());
        } else {
            content.add("");
        }
        if (this.proteinStart != null) {
            content.add(Integer.toString(this.proteinStart));
        } else {
            content.add("");
        }
        if (this.proteinEnd != null) {
            content.add(Integer.toString(this.proteinEnd));
        } else {
            content.add("");
        }
        if (this.refResidues != null) {
            content.add(this.refResidues);
        } else {
            content.add("");
        }
        if (this.variantResidues != null) {
            content.add(this.variantResidues);
        } else {
            content.add("");
        }
        if (this.referenceGenomes != null) {
            content.add(this.referenceGenomes.stream().map(referenceGenome -> referenceGenome.name()).collect(Collectors.joining(",")));
        } else {
            content.add("");
        }

        return StringUtils.join(content.toArray(), "&");
    }
}


