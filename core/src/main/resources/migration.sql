-- Update to v1.13
-- Applicable to v1.13~v1.19

drop table `evidence_clinical_trial`;
drop table `clinical_trial_country`;
drop table `clinical_trial_drug`;
drop table `clinical_trial`;

alter table `drug_synonym`
    change `synonym` `synonyms` varchar(255) null;

delete
from `evidence_alteration`
where evidence_id in (
    select evidence_id
    from evidence
    where evidence_type = 'NCCN_GUIDELINES'
);
drop table evidence_nccn_guideline;
delete
from evidence
where evidence_type = 'NCCN_GUIDELINES';
drop table nccn_guideline;

alter table evidence_treatment
    add priority int default 1 null;

alter table evidence_treatment
    add uuid varchar(40) null;

alter table evidence_treatment
    drop foreign key FK4E9950909D586F3F;
alter table treatment_approved_indications
    drop foreign key FK333C46769D586F3F;
alter table treatment_drug
    drop foreign key FK5720FB079D586F3F;
drop table treatment;
rename table evidence_treatment to treatment;

alter table treatment
    change treatment_id id int auto_increment;

alter table treatment
    drop primary key;

alter table treatment
    add constraint treatment_pk
        primary key (id);

alter table treatment
    add unique (evidence_id, id);

alter table treatment_approved_indications
    add constraint fk_treatment_approved_indications
        foreign key (treatment_id) references treatment (id);
alter table treatment_drug
    add constraint fk_treatment_drug
        foreign key (treatment_id) references treatment (id);

alter table treatment_drug
    add priority int default 1 null;



-- Update to v1.20
-- Applicable to v1.20~v1.23
drop table drug_atccode;

create table drug_family
(
    drug_id        int not null,
    drug_family_id int not null,
    primary key (drug_id, drug_family_id),
    constraint fk_drug_id_family_drug
        foreign key (drug_id) references drug (id),
    constraint fk_drug_family_family_drug
        foreign key (drug_family_id) references drug (id)
);
alter table drug
    modify drug_name longtext not null;

alter table drug
    add ncit_code varchar(20) null;

alter table drug
    add type varchar(20) null;

update drug
set drug.type='DRUG'
where drug.type is null;

alter table drug_synonym
    change synonyms synonym longtext null;



-- Update to v1.24
-- Applicable to v1.24
create table geneset
(
    id   int auto_increment
        primary key,
    name varchar(255) not null,
    uuid varchar(40)  not null
);


create table geneset_gene
(
    geneset_id     int not null,
    entrez_gene_id int not null,
    primary key (geneset_id, entrez_gene_id),
    constraint FKA19C3AE7C602BC92
        foreign key (entrez_gene_id) references gene (entrez_gene_id),
    constraint FKA19C3AE7C94E945F
        foreign key (geneset_id) references geneset (id)
);

rename table portalAlt_oncoKBAlt to portal_alteration_oncokb_alteration;
alter table portal_alteration_oncokb_alteration
    change alteration_id oncokb_alteration_id int not null;
alter table portal_alteration_oncokb_alteration
    change portalAlteration_id portal_alteration_id int not null;

alter table evidence
    change propagation solid_propagation_level varchar(10) null;
alter table evidence
    add for_germline bit null;
alter table evidence
    add liquid_propagation_level varchar(255) null;
alter table evidence
    add last_review datetime null;

alter table gene
    change curatedIsoform curated_isoform varchar(100) null;
alter table gene
    change curatedRefSeq curated_ref_seq varchar(100) null;
alter table gene
    change TSG tsg bit null;


-- Update to v2.0, this is update to the v2 Levels of Evidence https://www.oncokb.org/news#12202019
-- Applicable to v2.0~v2.7
update evidence set level_of_evidence='LEVEL_2' where level_of_evidence='LEVEL_2A';
update evidence set level_of_evidence='LEVEL_3B' where level_of_evidence='LEVEL_2B';
update evidence set solid_propagation_level='LEVEL_2' where solid_propagation_level='LEVEL_2A';
update evidence set solid_propagation_level='LEVEL_3B' where solid_propagation_level='LEVEL_2B';
update evidence set liquid_propagation_level='LEVEL_2' where liquid_propagation_level='LEVEL_2A';
update evidence set liquid_propagation_level='LEVEL_3B' where liquid_propagation_level='LEVEL_2B';

-- Update to v2.8
create table alteration_reference_genome
(
    alteration_id    int         not null,
    reference_genome varchar(10) null,
    constraint FKE8AFDC0BAAD5975
        foreign key (alteration_id) references alteration (id)
);

INSERT INTO alteration_reference_genome (alteration_id)
SELECT id
FROM alteration;
update alteration_reference_genome
set reference_genome='GRCh37'
where reference_genome is null;

alter table gene
    change curated_isoform grch37_isoform varchar(100) null;

alter table gene
    change curated_ref_seq grch37_ref_seq varchar(100) null;

alter table gene
    add grch38_isoform varchar(100) null;

alter table gene
    add grch38_ref_seq varchar(100) null;

-- Update to v3.0
-- This is not possible without complex cancer type mapping. We need to compute the cancer type of the evidence using the cancer_type table

-- Update to v3.2
create table `info` (
  `id` int(11) not null AUTO_INCREMENT,
  `data_version` varchar(255) default null,
  `data_version_date` datetime default null,
  `ncit_version` varchar(255) default null,
  `oncotree_version` varchar(255) default null,
  primary key (`id`)
);

insert into `info` values (1,'v3.2','2021-03-12','19.03d','oncotree_2019_12_01');

-- Update to v3.10
alter table evidence
    add fda_level varchar(255) null;
update evidence set fda_level='LEVEL_Fda2' where level_of_evidence in ('LEVEL_1', 'LEVEL_R1', 'LEVEL_2');
update evidence set fda_level='LEVEL_Fda3' where level_of_evidence in ('LEVEL_3A', 'LEVEL_4', 'LEVEL_R2');

-- Update to v4
alter table alteration
    modify alteration longtext null;
alter table alteration
    add protein_change longtext null after alteration;
alter table evidence
    add name longtext null after known_effect;
