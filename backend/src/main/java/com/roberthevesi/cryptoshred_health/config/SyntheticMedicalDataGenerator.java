package com.roberthevesi.cryptoshred_health.config;

import com.roberthevesi.cryptoshred_health.dto.GpRequest;
import com.roberthevesi.cryptoshred_health.dto.GpResponse;
import com.roberthevesi.cryptoshred_health.dto.PatientRequest;
import com.roberthevesi.cryptoshred_health.dto.PatientResponse;
import com.roberthevesi.cryptoshred_health.dto.PatientVisitRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class SyntheticMedicalDataGenerator {

    public record AttachmentSpec(String fileName, String title, String details) {}

    public record PatientTemplate(
            String patientId,
            String firstName,
            String lastName,
            String dateOfBirth,
            String gender,
            String email,
            String phoneNumber,
            String address,
            String nhsNumber,
            String bloodType,
            int heightCm,
            double weightKg,
            String baseAllergies,
            String baseChronicConditions,
            String emergencyContactName,
            String emergencyContactPhone,
            String emergencyContactRelationship,
            int primaryGpIndex
    ) {}

    public static class InMemoryMultipartFile implements MultipartFile {
        private final String filename;
        private final String contentType;
        private final byte[] content;

        public InMemoryMultipartFile(String filename, String contentType, byte[] content) {
            this.filename = filename;
            this.contentType = contentType;
            this.content = content != null ? content : new byte[0];
        }

        @Override public String getName() { return filename; }
        @Override public String getOriginalFilename() { return filename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() { return content; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }
        @Override public void transferTo(File dest) { throw new UnsupportedOperationException(); }
    }

    // ── 1. NHS GENERAL PRACTITIONERS & SPECIALISTS (10+ Specialties) ─────────
    public List<GpRequest> getPredefinedGps() {
        List<GpRequest> gps = new ArrayList<>();

        gps.add(createGp("Alistair", "Finch", "dr.finch@stmarys-surgery.nhs.uk", "+44 20 7946 0192",
                "GMC-7412984", "General Practice & Family Medicine", "St Mary's Health Centre, London"));

        gps.add(createGp("Clara", "Oswald", "dr.oswald@bakerst-medical.nhs.uk", "+44 20 7946 0833",
                "GMC-6391024", "Cardiology & Cardiovascular Medicine", "Baker Street Cardiology & Medical Centre, London"));

        gps.add(createGp("Rajesh", "Patel", "dr.patel@qe-health.nhs.uk", "+44 121 496 0194",
                "GMC-5182930", "Endocrinology & Metabolic Disorders", "Birmingham Queen Elizabeth Health Centre, Birmingham"));

        gps.add(createGp("Fiona", "Campbell", "dr.campbell@edinburgh-health.scot.nhs.uk", "+44 131 496 0281",
                "GMC-4829104", "Respiratory Medicine & Pulmonology", "Royal Edinburgh Respiratory Clinic, Edinburgh"));

        gps.add(createGp("Edward", "Rochester", "dr.rochester@addenbrookes.nhs.uk", "+44 1223 762190",
                "GMC-3918274", "Neurology & Neurophysiology", "Addenbrooke's Neurological Centre, Cambridge"));

        gps.add(createGp("Tariq", "Mansoor", "dr.mansoor@manchester-health.nhs.uk", "+44 161 496 0772",
                "GMC-6729103", "Orthopedic Surgery & Musculoskeletal Health", "Manchester Central Orthopedic Clinic, Manchester"));

        gps.add(createGp("Sophie", "Laurent", "dr.laurent@bristol-harbour.nhs.uk", "+44 117 496 0345",
                "GMC-7182945", "Dermatology & Cutaneous Medicine", "Bristol Harbourside Dermatology Centre, Bristol"));

        gps.add(createGp("David", "MacLeod", "dr.macleod@leeds-digestive.nhs.uk", "+44 113 496 0561",
                "GMC-5918234", "Gastroenterology & Hepatology", "Leeds Park Square Digestive Health, Leeds"));

        gps.add(createGp("Megan", "Davies", "dr.davies@cardiff-rheum.wales.nhs.uk", "+44 29 2018 0923",
                "GMC-4192837", "Rheumatology & Autoimmune Disorders", "Cardiff Bay Rheumatology Practice, Cardiff"));

        gps.add(createGp("Ciaran", "O'Reilly", "dr.oreilly@belfast-health.hscni.net", "+44 28 9018 0412",
                "GMC-8291045", "Psychiatry & Behavioral Health", "Belfast City Mental Health Centre, Belfast"));

        gps.add(createGp("Beatrice", "Sterling", "dr.sterling@oxford-radcliffe.nhs.uk", "+44 1865 496011",
                "GMC-3829102", "Internal Medicine & Geriatrics", "Oxford Radcliffe Medical Clinic, Oxford"));

        return gps;
    }

    private GpRequest createGp(String first, String last, String email, String phone, String gmc, String spec, String practice) {
        GpRequest gp = new GpRequest();
        gp.setFirstName(first);
        gp.setLastName(last);
        gp.setEmail(email);
        gp.setPhoneNumber(phone);
        gp.setGmcNumber(gmc);
        gp.setSpecialisation(spec);
        gp.setPracticeName(practice);
        return gp;
    }

    // ── 2. 100 DIVERSE PATIENT TEMPLATES ─────────────────────────────────────
    public List<PatientTemplate> getPredefinedPatients() {
        List<PatientTemplate> list = new ArrayList<>(100);

        // 1-10
        list.add(new PatientTemplate("PAT-10001", "Eleanor", "Vance", "1985-04-12", "Female", "eleanor.vance@example.com", "+44 7700 900142", "42 Hill House Lane, London NW1 4NP", "943 476 5919", "A+", 168, 64.5, "Penicillin, Latex", "Type 2 Diabetes Mellitus, Essential Hypertension", "John Vance", "+44 7700 900551", "Spouse", 0));
        list.add(new PatientTemplate("PAT-10002", "Marcus", "Thorne", "1992-09-25", "Male", "marcus.thorne@example.com", "+44 7700 900881", "17 Kensington Church Walk, London W8 4NB", "485 910 2384", "O-", 182, 79.0, "None Known", "Post-Surgical Appendectomy Recovery", "Claire Thorne", "+44 7700 900552", "Spouse", 1));
        list.add(new PatientTemplate("PAT-10003", "Sarah", "Jenkins", "1978-11-03", "Female", "sarah.jenkins@example.com", "+44 7700 900319", "88 Bloomsbury Way, London WC1A 2SE", "712 849 3015", "B+", 165, 72.0, "Sulfa Drugs", "Hyperlipidemia, Stage 1 Hypertension", "David Jenkins", "+44 7700 900553", "Spouse", 1));
        list.add(new PatientTemplate("PAT-10004", "Oliver", "Harrison", "1997-02-14", "Male", "oliver.harrison@example.com", "+44 7700 900404", "12 Deansgate Court, Manchester M1 1AD", "629 104 8831", "O+", 178, 74.0, "None Known", "Mild Intermittent Asthma", "Laura Harrison", "+44 7700 900554", "Parent", 5));
        list.add(new PatientTemplate("PAT-10005", "Amara", "Okafor", "1989-08-30", "Female", "amara.okafor@example.com", "+44 7700 900512", "73 Harborne Road, Birmingham B15 2TT", "814 293 7701", "A-", 170, 68.0, "Latex", "Hypothyroidism, Seasonal Allergic Rhinitis", "Chidi Okafor", "+44 7700 900555", "Brother", 2));
        list.add(new PatientTemplate("PAT-10006", "Callum", "Stewart", "1973-12-05", "Male", "callum.stewart@example.com", "+44 7700 900623", "29 Queen Street, Edinburgh EH3 9AB", "305 918 4426", "AB+", 185, 88.5, "Aspirin / NSAIDs", "Gastro-esophageal Reflux, Stage 2 Hypertension", "Morag Stewart", "+44 7700 900556", "Spouse", 3));
        list.add(new PatientTemplate("PAT-10007", "Priya", "Patel", "1984-06-19", "Female", "priya.patel@example.com", "+44 7700 900734", "15 Clifton Down Road, Bristol BS8 1TH", "521 830 9942", "B-", 162, 59.0, "Penicillin", "Migraine with Aura, Vitamin D Deficiency", "Amit Patel", "+44 7700 900557", "Spouse", 6));
        list.add(new PatientTemplate("PAT-10008", "Liam", "Gallagher", "1961-10-11", "Male", "liam.gallagher@example.com", "+44 7700 900845", "64 Briggate Crescent, Leeds LS2 9JT", "194 772 3058", "O+", 175, 83.0, "Codeine", "Type 2 Diabetes Mellitus, Osteoarthritis of Knee", "Paul Gallagher", "+44 7700 900558", "Brother", 7));
        list.add(new PatientTemplate("PAT-10009", "Siobhan", "Murphy", "1995-03-27", "Female", "siobhan.murphy@example.com", "+44 7700 900956", "41 Malone Road, Belfast BT9 6GH", "738 201 6492", "A+", 167, 61.0, "None Known", "Atopic Eczema, Generalized Anxiety Disorder", "Patrick Murphy", "+44 7700 900559", "Father", 9));
        list.add(new PatientTemplate("PAT-10010", "Rhys", "Griffiths", "1980-07-08", "Male", "rhys.griffiths@example.com", "+44 7700 900107", "58 Cathedral Road, Cardiff CF14 4XW", "492 810 5537", "O-", 180, 82.0, "Ciprofloxacin", "Lumbar Spondylosis, Chronic Back Pain", "Gwen Griffiths", "+44 7700 900560", "Spouse", 8));

        // 11-20
        list.add(new PatientTemplate("PAT-10011", "Emily", "Watson", "2001-05-16", "Female", "emily.watson@example.com", "+44 7700 900118", "19 Trumpington Street, Cambridge CB2 1TN", "861 940 2273", "B+", 166, 56.0, "None Known", "Tension Headache, Iron Deficiency Anemia", "Susan Watson", "+44 7700 900561", "Mother", 4));
        list.add(new PatientTemplate("PAT-10012", "Tariq", "Al-Mansoor", "1967-01-22", "Male", "tariq.almansoor@example.com", "+44 7700 900129", "83 Banbury Road, Oxford OX2 6HG", "375 882 1094", "A+", 176, 85.0, "Penicillin", "Hyperlipidemia, Essential Hypertension", "Samira Mansoor", "+44 7700 900562", "Spouse", 10));
        list.add(new PatientTemplate("PAT-10013", "Charlotte", "Davies", "1954-09-14", "Female", "charlotte.davies@example.com", "+44 7700 900130", "102 St Thomas Street, London SE1 7PB", "640 193 8527", "O+", 158, 63.0, "Aspirin", "Osteoporosis, Primary Hypothyroidism", "Edward Davies", "+44 7700 900563", "Son", 0));
        list.add(new PatientTemplate("PAT-10014", "Muhammad", "Khan", "1987-11-09", "Male", "muhammad.khan@example.com", "+44 7700 900141", "34 Moseley Village, Birmingham B13 8RA", "915 304 6821", "AB-", 181, 78.5, "None Known", "Moderate Persistent Asthma, Allergic Rhinitis", "Amina Khan", "+44 7700 900564", "Spouse", 2));
        list.add(new PatientTemplate("PAT-10015", "Fiona", "MacLeod", "1963-04-03", "Female", "fiona.macleod@example.com", "+44 7700 900152", "55 Holyrood Road, Edinburgh EH8 9YL", "284 719 5036", "A-", 163, 67.0, "Sulfa Drugs", "Rheumatoid Arthritis, Mild Hypertension", "Ian MacLeod", "+44 7700 900565", "Spouse", 3));
        list.add(new PatientTemplate("PAT-10016", "George", "Bennett", "1946-12-18", "Male", "george.bennett@example.com", "+44 7700 900163", "14 Wilmslow Road, Manchester M20 2UT", "539 821 4470", "O+", 172, 76.0, "None Known", "Atrial Fibrillation, Chronic Kidney Disease Stage 2", "Margaret Bennett", "+44 7700 900566", "Spouse", 5));
        list.add(new PatientTemplate("PAT-10017", "Chloe", "Taylor", "1999-08-07", "Non-Binary", "chloe.taylor@example.com", "+44 7700 900174", "9 Whiteladies Road, Bristol BS6 5UH", "172 905 3846", "B+", 169, 62.0, "Latex", "Acne Vulgaris, Generalized Anxiety", "Alex Taylor", "+44 7700 900567", "Partner", 6));
        list.add(new PatientTemplate("PAT-10018", "David", "Wright", "1975-03-31", "Male", "david.wright@example.com", "+44 7700 900185", "47 Headingley Lane, Leeds LS6 2ED", "846 109 2735", "A+", 184, 91.0, "Penicillin", "Gastro-esophageal Reflux, Fatty Liver Disease", "Rachel Wright", "+44 7700 900568", "Spouse", 7));
        list.add(new PatientTemplate("PAT-10019", "Aoife", "Byrne", "1991-06-25", "Female", "aoife.byrne@example.com", "+44 7700 900196", "23 University Road, Belfast BT7 1NN", "391 847 6205", "O-", 164, 58.0, "None Known", "Irritable Bowel Syndrome, Episodic Migraine", "Sean Byrne", "+44 7700 900569", "Spouse", 9));
        list.add(new PatientTemplate("PAT-10020", "Dylan", "Evans", "1982-10-12", "Male", "dylan.evans@example.com", "+44 7700 900207", "71 Newport Road, Cardiff CF24 0BB", "658 230 1947", "B+", 177, 84.0, "None Known", "Rotator Cuff Tendinopathy, Stage 1 Hypertension", "Carys Evans", "+44 7700 900570", "Spouse", 8));

        // 21-30
        list.add(new PatientTemplate("PAT-10021", "Grace", "Robinson", "1957-07-29", "Female", "grace.robinson@example.com", "+44 7700 900218", "38 Milton Road, Cambridge CB4 1YG", "420 915 8361", "A+", 160, 65.0, "Penicillin, Codeine", "Type 2 Diabetes Mellitus, Peripheral Neuropathy", "Thomas Robinson", "+44 7700 900571", "Spouse", 4));
        list.add(new PatientTemplate("PAT-10022", "Alexander", "Sterling", "1970-02-17", "Male", "alexander.sterling@example.com", "+44 7700 900229", "12 Headington Hill, Oxford OX3 7BN", "783 149 0528", "O+", 183, 86.0, "None Known", "Coronary Artery Disease, Hypercholesterolemia", "Victoria Sterling", "+44 7700 900572", "Spouse", 10));
        list.add(new PatientTemplate("PAT-10023", "Fatima", "Zahra", "1993-11-04", "Female", "fatima.zahra@example.com", "+44 7700 900230", "5 Aldersgate Street, London EC1A 1BB", "259 874 1630", "B-", 166, 60.0, "Sulfa Drugs", "Hypothyroidism, Polycystic Ovary Syndrome", "Zaid Zahra", "+44 7700 900573", "Spouse", 0));
        list.add(new PatientTemplate("PAT-10024", "Arthur", "Pendelton", "1942-05-20", "Male", "arthur.pendelton@example.com", "+44 7700 900241", "88 King Street, Manchester M3 4FN", "906 312 7485", "A-", 171, 73.0, "Latex", "Osteoarthritis of Bilateral Knees, Hypertension", "Dorothy Pendelton", "+44 7700 900574", "Spouse", 5));
        list.add(new PatientTemplate("PAT-10025", "Mia", "Thompson", "2003-09-11", "Female", "mia.thompson@example.com", "+44 7700 900252", "6 Bristol Road South, Birmingham B29 6JF", "541 728 3904", "O+", 168, 55.0, "None Known", "Atopic Eczema, Allergic Rhinitis", "Helen Thompson", "+44 7700 900575", "Mother", 2));
        list.add(new PatientTemplate("PAT-10026", "Eilidh", "Campbell", "1976-08-14", "Female", "eilidh.campbell@example.com", "+44 7700 900263", "92 Corstorphine Road, Edinburgh EH12 5XQ", "382 605 9173", "AB+", 164, 66.0, "Penicillin", "Fibromyalgia, Chronic Fatigue Syndrome", "Gordon Campbell", "+44 7700 900576", "Spouse", 3));
        list.add(new PatientTemplate("PAT-10027", "Jack", "Mitchell", "1988-04-26", "Male", "jack.mitchell@example.com", "+44 7700 900274", "31 Redcliffe Parade, Bristol BS1 4SS", "719 450 2836", "O-", 179, 81.0, "None Known", "Chronic Lumbar Strain, Work-Related Stress", "Emma Mitchell", "+44 7700 900577", "Spouse", 6));
        list.add(new PatientTemplate("PAT-10028", "Sophie", "Walker", "1996-12-02", "Female", "sophie.walker@example.com", "+44 7700 900285", "14 The Calls, Leeds LS1 5HD", "164 893 7205", "A+", 171, 63.0, "Latex", "Major Depressive Disorder (Mild), Insomnia", "James Walker", "+44 7700 900578", "Brother", 7));
        list.add(new PatientTemplate("PAT-10029", "Connor", "O'Neill", "1985-10-19", "Male", "connor.oneill@example.com", "+44 7700 900296", "77 Antrim Road, Belfast BT15 3GH", "837 201 5946", "B+", 186, 92.0, "Aspirin", "Gouty Arthritis, Essential Hypertension", "Maeve O'Neill", "+44 7700 900579", "Spouse", 9));
        list.add(new PatientTemplate("PAT-10030", "Cerys", "Hughes", "1972-03-08", "Female", "cerys.hughes@example.com", "+44 7700 900307", "45 Bute Street, Cardiff CF10 1EP", "495 182 6370", "O+", 162, 70.0, "Sulfa Drugs", "Psoriatic Arthritis, Plaque Psoriasis", "Alun Hughes", "+44 7700 900580", "Spouse", 8));

        // 31-40
        list.add(new PatientTemplate("PAT-10031", "Henry", "Foster", "1959-11-23", "Male", "henry.foster@example.com", "+44 7700 900318", "60 Hills Road, Cambridge CB1 2JW", "628 394 1057", "A-", 175, 80.0, "None Known", "Type 2 Diabetes Mellitus, Essential Hypertension", "Mary Foster", "+44 7700 900581", "Spouse", 4));
        list.add(new PatientTemplate("PAT-10032", "Olivia", "King", "1990-06-15", "Female", "olivia.king@example.com", "+44 7700 900329", "24 High Street, Oxford OX1 2JD", "350 917 4826", "B+", 167, 59.0, "Penicillin", "Migraine without Aura, Dyspepsia", "Harry King", "+44 7700 900582", "Spouse", 10));
        list.add(new PatientTemplate("PAT-10033", "Kwame", "Mensah", "1979-01-30", "Male", "kwame.mensah@example.com", "+44 7700 900330", "11 Victoria Street, London SW1A 1AA", "871 405 2963", "O+", 182, 87.0, "None Known", "Stage 2 Hypertension, Hypercholesterolemia", "Abena Mensah", "+44 7700 900583", "Spouse", 0));
        list.add(new PatientTemplate("PAT-10034", "Hannah", "Scott", "1998-07-21", "Female", "hannah.scott@example.com", "+44 7700 900341", "89 Wilbraham Road, Manchester M14 5GX", "219 638 5047", "A+", 165, 57.0, "Latex", "Asthma Exacerbation, Allergic Rhinitis", "David Scott", "+44 7700 900584", "Father", 5));
        list.add(new PatientTemplate("PAT-10035", "Ibrahim", "Ali", "1965-09-03", "Male", "ibrahim.ali@example.com", "+44 7700 900352", "22 New Street, Birmingham B1 1BB", "745 820 1936", "O-", 177, 85.5, "Codeine", "Type 2 Diabetes Mellitus, Non-Alcoholic Fatty Liver", "Yasmin Ali", "+44 7700 900585", "Spouse", 2));
        list.add(new PatientTemplate("PAT-10036", "Isla", "MacDonald", "1994-02-18", "Female", "isla.macdonald@example.com", "+44 7700 900363", "16 Royal Mile, Edinburgh EH1 1TH", "503 916 4287", "B-", 168, 62.0, "None Known", "Generalized Anxiety Disorder, Tension Headache", "Finlay MacDonald", "+44 7700 900586", "Spouse", 3));
        list.add(new PatientTemplate("PAT-10037", "Benjamin", "Green", "1952-11-10", "Male", "benjamin.green@example.com", "+44 7700 900374", "50 North Street, Bristol BS3 1TF", "968 152 7403", "AB+", 174, 82.0, "Sulfa Drugs", "Knee Osteoarthritis, Stage 1 Hypertension", "Judith Green", "+44 7700 900587", "Spouse", 6));
        list.add(new PatientTemplate("PAT-10038", "Jessica", "Adams", "1981-05-06", "Female", "jessica.adams@example.com", "+44 7700 900385", "33 Street Lane, Leeds LS8 1AL", "327 491 8560", "A+", 163, 68.0, "Penicillin", "Gastro-esophageal Reflux, Hypothyroidism", "Mark Adams", "+44 7700 900588", "Spouse", 7));
        list.add(new PatientTemplate("PAT-10039", "Ronan", "Doyle", "1968-08-28", "Male", "ronan.doyle@example.com", "+44 7700 900396", "10 Chichester Street, Belfast BT1 5GS", "684 039 2175", "O+", 181, 89.0, "None Known", "Hypercholesterolemia, Lumbar Spondylosis", "Clare Doyle", "+44 7700 900589", "Spouse", 9));
        list.add(new PatientTemplate("PAT-10040", "Megan", "Morgan", "2000-10-04", "Female", "megan.morgan@example.com", "+44 7700 900407", "42 Cowbridge Road East, Cardiff CF11 9AB", "152 790 4836", "B+", 166, 58.0, "None Known", "Atopic Eczema, Seasonal Rhinitis", "Gareth Morgan", "+44 7700 900590", "Father", 8));

        // 41-50
        list.add(new PatientTemplate("PAT-10041", "Wei", "Chen", "1986-12-12", "Male", "wei.chen@example.com", "+44 7700 900418", "7 Storey's Way, Cambridge CB3 0AJ", "893 241 6075", "O+", 176, 75.0, "None Known", "Episodic Tension Headache, Mild Gastritis", "Lin Chen", "+44 7700 900591", "Spouse", 4));
        list.add(new PatientTemplate("PAT-10042", "Zoe", "Palmer", "1996-04-17", "Non-Binary", "zoe.palmer@example.com", "+44 7700 900429", "55 Cowley Road, Oxford OX4 1UR", "417 805 3296", "A-", 172, 64.0, "Latex", "Generalized Anxiety Disorder, IBS", "Sam Palmer", "+44 7700 900592", "Partner", 10));
        list.add(new PatientTemplate("PAT-10043", "Thomas", "Bradley", "1950-03-09", "Male", "thomas.bradley@example.com", "+44 7700 900430", "19 Upper Street, London N1 9GU", "736 924 1580", "O-", 173, 77.0, "Aspirin", "Coronary Artery Disease, Osteoarthritis", "Ann Bradley", "+44 7700 900593", "Spouse", 0));
        list.add(new PatientTemplate("PAT-10044", "Ruby", "Cooper", "2005-08-15", "Female", "ruby.cooper@example.com", "+44 7700 900441", "28 Oldham Street, Manchester M4 1HQ", "580 163 9427", "B+", 164, 53.0, "Penicillin", "Moderate Acne Vulgaris, Dysmenorrhea", "Sarah Cooper", "+44 7700 900594", "Mother", 5));
        list.add(new PatientTemplate("PAT-10045", "Zainab", "Begum", "1971-06-27", "Female", "zainab.begum@example.com", "+44 7700 900452", "94 Stratford Road, Birmingham B11 4AR", "924 517 3806", "A+", 159, 71.0, "Sulfa Drugs", "Type 2 Diabetes Mellitus, Essential Hypertension", "Rashid Begum", "+44 7700 900595", "Spouse", 2));
        list.add(new PatientTemplate("PAT-10046", "Hamish", "Fraser", "1977-10-01", "Male", "hamish.fraser@example.com", "+44 7700 900463", "61 Queensferry Road, Edinburgh EH4 2PF", "361 809 4523", "O+", 183, 89.0, "None Known", "Asthma, Gastro-esophageal Reflux", "Catriona Fraser", "+44 7700 900596", "Spouse", 3));
        list.add(new PatientTemplate("PAT-10047", "Alice", "Shaw", "1960-01-19", "Female", "alice.shaw@example.com", "+44 7700 900474", "18 Gloucester Road, Bristol BS7 8NX", "608 425 1793", "AB-", 161, 66.0, "None Known", "Rheumatoid Arthritis, Osteoporosis", "Peter Shaw", "+44 7700 900597", "Spouse", 6));
        list.add(new PatientTemplate("PAT-10048", "Daniel", "Richardson", "1989-09-24", "Male", "daniel.richardson@example.com", "+44 7700 900485", "40 Chapeltown Road, Leeds LS7 3EB", "247 930 6815", "A-", 180, 81.0, "Ciprofloxacin", "Plantar Fasciitis, Tension Headache", "Laura Richardson", "+44 7700 900598", "Spouse", 7));
        list.add(new PatientTemplate("PAT-10049", "Niamh", "Kelly", "1983-07-13", "Female", "niamh.kelly@example.com", "+44 7700 900496", "82 Holywood Road, Belfast BT4 1NX", "875 192 3406", "O+", 167, 63.0, "Latex", "Hypothyroidism, Chronic Migraine", "Liam Kelly", "+44 7700 900599", "Spouse", 9));
        list.add(new PatientTemplate("PAT-10050", "Owain", "Powell", "1966-11-05", "Male", "owain.powell@example.com", "+44 7700 900507", "39 Albany Road, Cardiff CF23 5AB", "413 658 9027", "B+", 178, 87.0, "Penicillin", "Stage 2 Hypertension, Hyperlipidemia", "Bethan Powell", "+44 7700 900600", "Spouse", 8));

        // 51-60
        list.add(new PatientTemplate("PAT-10051", "Lily", "Henderson", "2002-04-20", "Female", "lily.henderson@example.com", "+44 7700 900518", "12 Newmarket Road, Cambridge CB5 8BL", "790 324 5168", "A+", 165, 55.0, "None Known", "Atopic Eczema, Iron Deficiency Anemia", "Richard Henderson", "+44 7700 900601", "Father", 4));
        list.add(new PatientTemplate("PAT-10052", "Sebastian", "Cruz", "1988-08-11", "Male", "sebastian.cruz@example.com", "+44 7700 900529", "74 Walton Street, Oxford OX2 7HT", "136 849 2075", "O+", 179, 78.0, "None Known", "Irritable Bowel Syndrome, Work Stress", "Elena Cruz", "+44 7700 900602", "Spouse", 10));
        list.add(new PatientTemplate("PAT-10053", "Maya", "Patel", "1979-05-23", "Female", "maya.patel@example.com", "+44 7700 900530", "49 Commercial Street, London E1 6AN", "582 910 4367", "B-", 163, 69.0, "Sulfa Drugs", "Type 2 Diabetes Mellitus, Dyslipidemia", "Sanjay Patel", "+44 7700 900603", "Spouse", 0));
        list.add(new PatientTemplate("PAT-10054", "James", "Wilson", "1944-02-14", "Male", "james.wilson@example.com", "+44 7700 900541", "105 Cheetham Hill Road, Manchester M8 8EP", "921 475 8036", "A-", 170, 74.0, "Codeine", "Atrial Fibrillation, Osteoarthritis of Hip", "Mary Wilson", "+44 7700 900604", "Spouse", 5));
        list.add(new PatientTemplate("PAT-10055", "Aisha", "Hussain", "1992-12-08", "Female", "aisha.hussain@example.com", "+44 7700 900552", "37 Hagley Road, Birmingham B16 8QQ", "348 602 9174", "O+", 168, 62.0, "Penicillin", "Migraine with Aura, Hypothyroidism", "Tariq Hussain", "+44 7700 900605", "Spouse", 2));
        list.add(new PatientTemplate("PAT-10056", "Lewis", "MacIntyre", "1956-07-02", "Male", "lewis.macintyre@example.com", "+44 7700 900563", "84 Newington Road, Edinburgh EH9 1UW", "675 239 8401", "B+", 181, 86.0, "None Known", "COPD (Moderate), Essential Hypertension", "Isobel MacIntyre", "+44 7700 900606", "Spouse", 3));
        list.add(new PatientTemplate("PAT-10057", "Poppy", "Barnes", "1997-03-16", "Female", "poppy.barnes@example.com", "+44 7700 900574", "26 Old Market Street, Bristol BS2 0JP", "209 854 1736", "A+", 167, 58.0, "Latex", "Plaque Psoriasis, Anxiety", "Luke Barnes", "+44 7700 900607", "Spouse", 6));
        list.add(new PatientTemplate("PAT-10058", "Nathanial", "Wood", "1974-09-29", "Male", "nathanial.wood@example.com", "+44 7700 900585", "52 Kirkstall Road, Leeds LS3 1AA", "834 167 5290", "O-", 185, 93.0, "Aspirin", "Gastro-esophageal Reflux, Lumbar Disc Prolapse", "Clare Wood", "+44 7700 900608", "Spouse", 7));
        list.add(new PatientTemplate("PAT-10059", "Erin", "Gallagher", "2004-11-17", "Female", "erin.gallagher@example.com", "+44 7700 900596", "15 Castlereagh Road, Belfast BT6 8EE", "462 703 9815", "AB+", 166, 57.0, "None Known", "Mild Persistent Asthma, Allergic Rhinitis", "Sean Gallagher", "+44 7700 900609", "Father", 9));
        list.add(new PatientTemplate("PAT-10060", "Gareth", "Thomas", "1962-06-10", "Male", "gareth.thomas@example.com", "+44 7700 900607", "98 Cowbridge Road West, Cardiff CF5 2YX", "715 984 2360", "A+", 176, 83.0, "Sulfa Drugs", "Gouty Arthritis, Stage 2 Hypertension", "Rhian Thomas", "+44 7700 900610", "Spouse", 8));

        // 61-70
        list.add(new PatientTemplate("PAT-10061", "Freya", "Chapman", "1987-01-25", "Female", "freya.chapman@example.com", "+44 7700 900618", "41 Long Road, Cambridge CB2 8PQ", "390 421 8675", "O+", 169, 65.0, "Penicillin", "Hypothyroidism, Fibromyalgia", "Mark Chapman", "+44 7700 900611", "Spouse", 4));
        list.add(new PatientTemplate("PAT-10062", "Lucas", "Silva", "1991-10-09", "Male", "lucas.silva@example.com", "+44 7700 900629", "18 St Aldate's, Oxford OX1 4AU", "648 153 7920", "B+", 180, 79.0, "None Known", "Tension Headache, Gastro-esophageal Reflux", "Mariana Silva", "+44 7700 900612", "Spouse", 10));
        list.add(new PatientTemplate("PAT-10063", "Zara", "Ahmed", "1980-04-14", "Female", "zara.ahmed@example.com", "+44 7700 900630", "72 Greenwich High Road, London SE10 0ER", "173 892 4506", "A-", 162, 68.0, "None Known", "Type 2 Diabetes Mellitus, Hypertension", "Farooq Ahmed", "+44 7700 900613", "Spouse", 0));
        list.add(new PatientTemplate("PAT-10064", "William", "Turner", "1948-12-03", "Male", "william.turner@example.com", "+44 7700 900641", "33 Seymour Grove, Manchester M16 0RA", "829 506 1437", "O+", 174, 78.0, "Codeine", "Osteoarthritis of Right Knee, Hyperlipidemia", "Joan Turner", "+44 7700 900614", "Spouse", 5));
        list.add(new PatientTemplate("PAT-10065", "Ananya", "Sharma", "1996-07-18", "Female", "ananya.sharma@example.com", "+44 7700 900652", "65 Harborne High Street, Birmingham B17 9TU", "506 317 8942", "B-", 165, 58.0, "Latex", "Atopic Eczema, Generalized Anxiety", "Rohan Sharma", "+44 7700 900615", "Spouse", 2));
        list.add(new PatientTemplate("PAT-10066", "Angus", "Sutherland", "1969-03-30", "Male", "angus.sutherland@example.com", "+44 7700 900663", "19 Morningside Road, Edinburgh EH10 4BF", "951 840 2637", "A+", 184, 90.0, "Penicillin", "Stage 1 Hypertension, Obstructive Sleep Apnoea", "Kirsty Sutherland", "+44 7700 900616", "Spouse", 3));
        list.add(new PatientTemplate("PAT-10067", "Rosie", "Collins", "1955-08-22", "Female", "rosie.collins@example.com", "+44 7700 900674", "8 Stoke Hill, Bristol BS9 1SB", "384 729 5160", "O-", 159, 64.0, "Sulfa Drugs", "Rheumatoid Arthritis, Osteopenia", "Brian Collins", "+44 7700 900617", "Spouse", 6));
        list.add(new PatientTemplate("PAT-10068", "Samuel", "Bailey", "1983-02-05", "Male", "samuel.bailey@example.com", "+44 7700 900685", "54 Dewsbury Road, Leeds LS11 9HG", "617 935 8042", "AB+", 178, 85.0, "None Known", "Rotator Cuff Tendinopathy, Back Pain", "Emma Bailey", "+44 7700 900618", "Spouse", 7));
        list.add(new PatientTemplate("PAT-10069", "Maeve", "Connolly", "1967-10-28", "Female", "maeve.connolly@example.com", "+44 7700 900696", "102 Falls Road, Belfast BT12 6HR", "240 681 9573", "A+", 163, 72.0, "Aspirin", "Hypothyroidism, Stage 2 Hypertension", "Declan Connolly", "+44 7700 900619", "Spouse", 9));
        list.add(new PatientTemplate("PAT-10070", "Ieuan", "Price", "1994-09-15", "Male", "ieuan.price@example.com", "+44 7700 900707", "27 Pantbach Road, Cardiff CF14 3NG", "895 372 4106", "O+", 182, 80.0, "None Known", "Moderate Asthma, Allergic Rhinitis", "Megan Price", "+44 7700 900620", "Spouse", 8));

        // 71-80
        list.add(new PatientTemplate("PAT-10071", "Jasmine", "Kaur", "1998-05-11", "Female", "jasmine.kaur@example.com", "+44 7700 900718", "83 Mill Road, Cambridge CB1 3EG", "432 109 7685", "B+", 167, 60.0, "None Known", "Episodic Migraine, Dyspepsia", "Gurpreet Kaur", "+44 7700 900621", "Brother", 4));
        list.add(new PatientTemplate("PAT-10072", "Dominic", "Webb", "1964-08-04", "Male", "dominic.webb@example.com", "+44 7700 900729", "35 London Road, Oxford OX3 0BP", "769 548 1230", "A-", 177, 84.0, "Penicillin", "Hypercholesterolemia, Essential Hypertension", "Sarah Webb", "+44 7700 900622", "Spouse", 10));
        list.add(new PatientTemplate("PAT-10073", "Layla", "Mahmoud", "1985-06-29", "Female", "layla.mahmoud@example.com", "+44 7700 900730", "14 Edgware Road, London W2 1NY", "185 294 6370", "O+", 164, 66.0, "Latex", "Type 2 Diabetes Mellitus, Hypothyroidism", "Kareem Mahmoud", "+44 7700 900623", "Spouse", 0));
        list.add(new PatientTemplate("PAT-10074", "Charles", "Booth", "1941-11-19", "Male", "charles.booth@example.com", "+44 7700 900741", "67 Barlow Moor Road, Manchester M21 9EG", "624 810 5937", "B-", 171, 75.0, "Codeine", "Atrial Fibrillation, Osteoarthritis of Knee", "Elizabeth Booth", "+44 7700 900624", "Spouse", 5));
        list.add(new PatientTemplate("PAT-10075", "Fatima", "Malik", "1973-04-16", "Female", "fatima.malik@example.com", "+44 7700 900752", "48 Ladypool Road, Birmingham B12 0HJ", "978 351 2460", "A+", 160, 70.0, "Sulfa Drugs", "Gastro-esophageal Reflux, Stage 1 Hypertension", "Tariq Malik", "+44 7700 900625", "Spouse", 2));
        list.add(new PatientTemplate("PAT-10076", "Craig", "Ferguson", "1990-12-07", "Male", "craig.ferguson@example.com", "+44 7700 900763", "79 Leith Walk, Edinburgh EH6 6QQ", "316 794 8520", "O-", 183, 86.0, "None Known", "Lumbar Disc Prolapse, Work-Related Stress", "Alison Ferguson", "+44 7700 900626", "Spouse", 3));
        list.add(new PatientTemplate("PAT-10077", "Harriet", "Lloyd", "1958-09-02", "Female", "harriet.lloyd@example.com", "+44 7700 900774", "22 Church Road, Bristol BS5 6TG", "549 082 1736", "AB-", 162, 65.0, "Penicillin", "Rheumatoid Arthritis, Hypothyroidism", "Edward Lloyd", "+44 7700 900627", "Spouse", 6));
        list.add(new PatientTemplate("PAT-10078", "Peter", "Dawson", "1978-07-26", "Male", "peter.dawson@example.com", "+44 7700 900785", "43 Cardigan Road, Leeds LS4 2PR", "803 627 9415", "A+", 179, 88.0, "None Known", "Hypercholesterolemia, Gout", "Helen Dawson", "+44 7700 900628", "Spouse", 7));
        list.add(new PatientTemplate("PAT-10079", "Ciara", "Magee", "1989-01-14", "Female", "ciara.magee@example.com", "+44 7700 900796", "59 Saintfield Road, Belfast BT8 7XP", "271 953 4860", "O+", 168, 61.0, "Latex", "Generalized Anxiety Disorder, IBS", "Niall Magee", "+44 7700 900629", "Spouse", 9));
        list.add(new PatientTemplate("PAT-10080", "Osian", "Jenkins", "1953-05-31", "Male", "osian.jenkins@example.com", "+44 7700 900807", "14 Newport Road, Cardiff CF3 2WJ", "738 416 0295", "B+", 175, 81.0, "Aspirin", "Osteoarthritis, Stage 2 Hypertension", "Glenys Jenkins", "+44 7700 900630", "Spouse", 8));

        // 81-90
        list.add(new PatientTemplate("PAT-10081", "Beatrice", "Ward", "1945-10-23", "Female", "beatrice.ward@example.com", "+44 7700 900818", "91 Arbury Road, Cambridge CB4 3ER", "460 197 8352", "A-", 158, 62.0, "Penicillin, Sulfa", "Osteoporosis, Primary Hypothyroidism", "Arthur Ward", "+44 7700 900631", "Son", 4));
        list.add(new PatientTemplate("PAT-10082", "Jordan", "Ellis", "2001-09-08", "Non-Binary", "jordan.ellis@example.com", "+44 7700 900829", "32 Iffley Road, Oxford OX4 2ES", "914 832 5706", "O+", 174, 66.0, "None Known", "Atopic Eczema, Allergic Rhinitis", "Morgan Ellis", "+44 7700 900632", "Partner", 10));
        list.add(new PatientTemplate("PAT-10083", "Victor", "Osei", "1972-11-12", "Male", "victor.osei@example.com", "+44 7700 900830", "110 George Street, London CR0 2RF", "157 604 9283", "B+", 181, 89.0, "Codeine", "Stage 2 Hypertension, Hyperlipidemia", "Akosua Osei", "+44 7700 900633", "Spouse", 0));
        list.add(new PatientTemplate("PAT-10084", "Evelyn", "Shaw", "1999-04-01", "Female", "evelyn.shaw@example.com", "+44 7700 900841", "27 Barton Road, Manchester M30 0WT", "683 249 7150", "A+", 166, 58.0, "None Known", "Tension Headache, Iron Deficiency", "Paul Shaw", "+44 7700 900634", "Father", 5));
        list.add(new PatientTemplate("PAT-10085", "Yusuf", "Rahman", "1984-08-20", "Male", "yusuf.rahman@example.com", "+44 7700 900852", "143 Alum Rock Road, Birmingham B8 2KS", "329 571 8640", "O-", 177, 82.0, "Penicillin", "Type 2 Diabetes Mellitus, Fatty Liver", "Saira Rahman", "+44 7700 900635", "Spouse", 2));
        list.add(new PatientTemplate("PAT-10086", "Morag", "Ross", "1962-03-15", "Female", "morag.ross@example.com", "+44 7700 900863", "56 Portobello High Street, Edinburgh EH15 1DE", "872 406 1953", "AB+", 165, 73.0, "Latex", "Rheumatoid Arthritis, Stage 1 Hypertension", "Kenneth Ross", "+44 7700 900636", "Spouse", 3));
        list.add(new PatientTemplate("PAT-10087", "Maxwell", "Fox", "1995-12-28", "Male", "maxwell.fox@example.com", "+44 7700 900874", "88 Wells Road, Bristol BS4 3DY", "516 938 2704", "B-", 182, 79.0, "None Known", "Moderate Asthma, Seasonal Allergies", "Jessica Fox", "+44 7700 900637", "Spouse", 6));
        list.add(new PatientTemplate("PAT-10088", "Amber", "Pearson", "1982-06-17", "Female", "amber.pearson@example.com", "+44 7700 900885", "101 Harrogate Road, Leeds LS17 5QW", "940 782 3516", "A+", 170, 67.0, "Sulfa Drugs", "Gastro-esophageal Reflux, Chronic Migraine", "David Pearson", "+44 7700 900638", "Spouse", 7));
        list.add(new PatientTemplate("PAT-10089", "Patrick", "McCann", "1960-10-30", "Male", "patrick.mccann@example.com", "+44 7700 900896", "48 Glen Road, Belfast BT11 9PB", "285 319 6470", "O+", 176, 85.0, "Aspirin", "Gouty Arthritis, Essential Hypertension", "Bridie McCann", "+44 7700 900639", "Spouse", 9));
        list.add(new PatientTemplate("PAT-10090", "Lowri", "Bowen", "1992-02-22", "Female", "lowri.bowen@example.com", "+44 7700 900907", "76 Romilly Road, Cardiff CF5 1DN", "761 048 5923", "B+", 164, 59.0, "None Known", "Atopic Eczema, Generalized Anxiety", "Dafydd Bowen", "+44 7700 900640", "Spouse", 8));

        // 91-100
        list.add(new PatientTemplate("PAT-10091", "Toby", "Fletcher", "1996-11-06", "Male", "toby.fletcher@example.com", "+44 7700 900918", "53 Cherry Hinton Road, Cambridge CB1 9NJ", "428 695 1307", "A-", 183, 80.0, "None Known", "Lumbar Muscle Strain, Work-Related Stress", "Chloe Fletcher", "+44 7700 900641", "Spouse", 4));
        list.add(new PatientTemplate("PAT-10092", "Imogen", "Hayes", "1975-08-19", "Female", "imogen.hayes@example.com", "+44 7700 900929", "29 Woodstock Road, Oxford OX2 8LA", "694 150 8273", "O+", 168, 68.0, "Penicillin", "Hypothyroidism, Stage 1 Hypertension", "Simon Hayes", "+44 7700 900642", "Spouse", 10));
        list.add(new PatientTemplate("PAT-10093", "Rohan", "Gupta", "1987-03-04", "Male", "rohan.gupta@example.com", "+44 7700 900930", "15 The Broadway, Southall, London UB1 3HE", "139 725 4860", "B+", 178, 86.0, "Latex", "Type 2 Diabetes Mellitus, Hyperlipidemia", "Pooja Gupta", "+44 7700 900643", "Spouse", 0));
        list.add(new PatientTemplate("PAT-10094", "Shirley", "Mason", "1947-06-25", "Female", "shirley.mason@example.com", "+44 7700 900941", "44 Rochdale Road, Manchester M9 5TY", "852 301 9746", "A+", 160, 71.0, "Codeine", "Osteoarthritis of Bilateral Knees, Osteoporosis", "Harold Mason", "+44 7700 900644", "Spouse", 5));
        list.add(new PatientTemplate("PAT-10095", "Farhan", "Siddiqui", "1979-10-18", "Male", "farhan.siddiqui@example.com", "+44 7700 900952", "85 Coventry Road, Birmingham B10 0AA", "574 918 2036", "O-", 180, 88.0, "Sulfa Drugs", "Gastro-esophageal Reflux, Stage 2 Hypertension", "Fatima Siddiqui", "+44 7700 900645", "Spouse", 2));
        list.add(new PatientTemplate("PAT-10096", "Kirsty", "Grant", "1993-05-12", "Female", "kirsty.grant@example.com", "+44 7700 900963", "34 Easter Road, Edinburgh EH7 4HN", "907 263 8415", "AB+", 167, 61.0, "None Known", "Moderate Asthma, Allergic Rhinitis", "Callum Grant", "+44 7700 900646", "Spouse", 3));
        list.add(new PatientTemplate("PAT-10097", "Leon", "Clarke", "1971-12-09", "Male", "leon.clarke@example.com", "+44 7700 900974", "99 Fishponds Road, Bristol BS16 5ST", "341 890 5627", "A+", 185, 94.0, "Penicillin", "Hypercholesterolemia, Rotator Cuff Tendinopathy", "Grace Clarke", "+44 7700 900647", "Spouse", 6));
        list.add(new PatientTemplate("PAT-10098", "Natalie", "Sharp", "1997-09-03", "Female", "natalie.sharp@example.com", "+44 7700 900985", "62 York Road, Leeds LS9 8AH", "786 425 1093", "O+", 163, 56.0, "None Known", "Atopic Dermatitis, Generalized Anxiety", "Oliver Sharp", "+44 7700 900648", "Spouse", 7));
        list.add(new PatientTemplate("PAT-10099", "Dermot", "Higgins", "1955-01-28", "Male", "dermot.higgins@example.com", "+44 7700 900996", "118 Crumlin Road, Belfast BT14 6QU", "213 579 6840", "B-", 175, 82.0, "Aspirin", "Gouty Arthritis, Stage 1 Hypertension", "Mary Higgins", "+44 7700 900649", "Spouse", 9));
        list.add(new PatientTemplate("PAT-10100", "Bethan", "Jones", "1988-07-14", "Female", "bethan.jones@example.com", "+44 7700 900100", "51 Heol-y-Deri, Rhiwbina, Cardiff CF14 1UJ", "659 804 3172", "A+", 169, 64.0, "None Known", "Hypothyroidism, Chronic Migraine with Aura", "Gethin Jones", "+44 7700 900650", "Spouse", 8));

        return list;
    }

    public PatientRequest toPatientRequest(PatientTemplate t, List<GpResponse> seededGps) {
        PatientRequest req = new PatientRequest();
        req.setPatientId(t.patientId());
        req.setFirstName(t.firstName());
        req.setLastName(t.lastName());
        req.setDateOfBirth(t.dateOfBirth());
        req.setGender(t.gender());
        req.setEmail(t.email());
        req.setPhoneNumber(t.phoneNumber());
        req.setAddress(t.address());
        req.setNhsNumber(t.nhsNumber());

        if (seededGps != null && !seededGps.isEmpty()) {
            int idx = t.primaryGpIndex() % seededGps.size();
            req.setGpId(seededGps.get(idx).getId());
        }
        return req;
    }

    // ── 3. 10 CLINICAL VISIT SCENARIOS PER PATIENT ───────────────────────────
    public List<PatientVisitRequest> generateVisitsForPatient(PatientTemplate pt, int patientIndex, List<GpResponse> seededGps) {
        List<PatientVisitRequest> visits = new ArrayList<>(10);

        // Calculate chronological dates spanning 12-24 months in the past
        // Patient index offset ensures slight realistic spread across the calendar
        int dayOffset = patientIndex % 10;
        LocalDate[] visitDates = new LocalDate[]{
                LocalDate.of(2024, 9, 2).plusDays(dayOffset),
                LocalDate.of(2024, 11, 14).plusDays(dayOffset),
                LocalDate.of(2025, 1, 20).plusDays(dayOffset),
                LocalDate.of(2025, 3, 25).plusDays(dayOffset),
                LocalDate.of(2025, 6, 5).plusDays(dayOffset),
                LocalDate.of(2025, 8, 18).plusDays(dayOffset),
                LocalDate.of(2025, 10, 28).plusDays(dayOffset),
                LocalDate.of(2026, 1, 15).plusDays(dayOffset),
                LocalDate.of(2026, 4, 10).plusDays(dayOffset),
                LocalDate.of(2026, 7, 8).plusDays(dayOffset)
        };

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (int v = 0; v < 10; v++) {
            LocalDate vDate = visitDates[v];
            LocalDate fDate = vDate.plusMonths(3).plusDays(14);

            PatientVisitRequest req = createVisitScenario(v, pt, patientIndex, vDate.format(dtf), fDate.format(dtf), seededGps);
            visits.add(req);
        }

        return visits;
    }

    private PatientVisitRequest createVisitScenario(int visitIdx, PatientTemplate pt, int patientIndex,
                                                   String visitDate, String followUpDate, List<GpResponse> seededGps) {
        PatientVisitRequest req = new PatientVisitRequest();

        req.setPatientName(pt.firstName() + " " + pt.lastName());
        req.setMrn(pt.patientId());
        req.setDateOfBirth(pt.dateOfBirth());
        req.setGender(pt.gender());
        req.setBloodType(pt.bloodType());
        req.setHeightCm(pt.heightCm() + " cm");
        req.setWeightKg(String.format(Locale.US, "%.1f kg", pt.weightKg()));

        double bmiVal = pt.weightKg() / Math.pow(pt.heightCm() / 100.0, 2);
        req.setBmi(String.format(Locale.US, "%.1f", bmiVal));

        req.setAllergies(pt.baseAllergies());
        req.setChronicConditions(pt.baseChronicConditions());
        req.setImmunizationStatus("Up-to-date (Annual Influenza Booster, COVID-19 Autumn Booster, Tetanus 2022)");
        req.setLifestyleFactors("Non-smoker, 2-4 units alcohol/week, moderate aerobic exercise 3x/week");
        req.setFollowUpDate(followUpDate);

        req.setPhone(pt.phoneNumber());
        req.setEmail(pt.email());
        req.setAddress(pt.address());
        req.setEmergencyContactName(pt.emergencyContactName());
        req.setEmergencyContactPhone(pt.emergencyContactPhone());
        req.setEmergencyContactRelationship(pt.emergencyContactRelationship());

        String[] insurers = {"NHS Standard Care", "Bupa Global Private", "AXA Health UK", "Aviva Health", "VitalityHealth UK"};
        req.setInsuranceProvider(insurers[(patientIndex + visitIdx) % insurers.length]);
        req.setInsurancePolicyNumber("NHS-POL-" + (940000 + patientIndex * 10 + visitIdx));
        req.setInsuranceGroupNumber("GRP-UK-" + (8000 + (patientIndex % 50)));

        switch (visitIdx) {
            case 0 -> populateGeneralPracticeVisit(req, patientIndex);
            case 1 -> populateCardiologyVisit(req, patientIndex);
            case 2 -> populateEndocrinologyVisit(req, patientIndex);
            case 3 -> populateRespiratoryVisit(req, patientIndex);
            case 4 -> populateNeurologyVisit(req, patientIndex);
            case 5 -> populateOrthopedicsVisit(req, patientIndex);
            case 6 -> populateDermatologyVisit(req, patientIndex);
            case 7 -> populateGastroenterologyVisit(req, patientIndex);
            case 8 -> populateRheumatologyVisit(req, patientIndex);
            default -> populatePsychiatryVisit(req, patientIndex);
        }

        return req;
    }

    // ── Individual Specialty Scenario Builders ────────────────────────────────

    private void populateGeneralPracticeVisit(PatientVisitRequest req, int pIdx) {
        req.setAttendingDoctor("Dr. Alistair Finch");
        req.setDepartment("General Practice & Family Medicine");
        req.setBloodPressure((118 + (pIdx % 8)) + "/" + (74 + (pIdx % 6)) + " mmHg");
        req.setHeartRate(68 + (pIdx % 10));
        req.setRespiratoryRate("15 breaths/min");
        req.setTemperature("36.7 °C");
        req.setOxygenSaturation("99%");
        req.setPainScore(0);

        req.setChiefComplaint("Routine annual health examination and review of mild fatigue");
        req.setDiagnosis("Z00.00 - General adult medical examination with E55.9 - Vitamin D deficiency, unspecified");
        req.setPrescriptions("Cholecalciferol 20,000 IU PO weekly for 6 weeks, then 800 IU PO OD maintenance");
        req.setMedicalNotes("Annual wellness screening completed. Cardiovascular risk score low. Cervical/breast screening up to date where applicable.");

        req.setSoapSubjective("Patient reports general wellbeing over the past 6 months, noting mild afternoon lethargy and winter fatigue. Denies weight loss, night sweats, fevers, or breathlessness. Diet is balanced with low dairy intake. Normal bowel and urinary habits.");
        req.setSoapObjective("Alert, oriented x3, well-nourished. Oropharynx clear without tonsillar exudate. Cervical lymph nodes non-palpable. Heart: normal S1/S2, no murmurs. Lungs: clear to auscultation bilaterally. Abdomen soft, non-tender. Full blood count normal; serum 25-OH Vitamin D low at 28 nmol/L.");
        req.setSoapAssessment("Healthy adult presenting for annual health check with biochemically proven hypovitaminosis D. General cardiovascular profile within recommended NHS guidelines.");
        req.setSoapPlan("1. Prescribed high-dose Vitamin D3 replacement followed by daily maintenance.\n2. Advised 20-30 mins daily outdoor exposure and dietary Vitamin D sources.\n3. Repeat serum 25-OH Vitamin D and bone profile in 6 months.\n4. Routine follow-up in 12 months or PRN.");
    }

    private void populateCardiologyVisit(PatientVisitRequest req, int pIdx) {
        req.setAttendingDoctor("Dr. Clara Oswald");
        req.setDepartment("Cardiology Clinic");
        req.setBloodPressure((142 + (pIdx % 12)) + "/" + (88 + (pIdx % 8)) + " mmHg");
        req.setHeartRate(78 + (pIdx % 12));
        req.setRespiratoryRate("16 breaths/min");
        req.setTemperature("36.8 °C");
        req.setOxygenSaturation("99%");
        req.setPainScore(0);

        req.setChiefComplaint("Cardiovascular risk evaluation and elevated home blood pressure readings");
        req.setDiagnosis("I10 - Essential (primary) hypertension & E78.0 - Pure hypercholesterolemia");
        req.setPrescriptions("Ramipril 5mg PO OD (morning), Atorvastatin 20mg PO ON");
        req.setMedicalNotes("12-Lead ECG demonstrates normal sinus rhythm with rare unifocal PVCs. QRISK3 10-year CVD risk calculated at 14.2%.");

        req.setSoapSubjective("Patient presents following elevated ambulatory blood pressure monitoring (averaging 144/90 mmHg). Notes occasional brief fluttering palpitations when resting, but denies exertional chest pain, orthopnea, paroxysmal nocturnal dyspnea, or presyncope.");
        req.setSoapObjective("BP 144/90 mmHg (right arm sitting, repeated after 5 min rest). Heart sounds S1 and S2 present, regular rhythm, no murmurs, rubs, or gallops. Carotid pulses brisk and symmetric without bruits. Peripheral pulses 2+ throughout. No pedal edema. Lipid panel: Total cholesterol 5.8 mmol/L, LDL 3.6 mmol/L, Triglycerides 1.8 mmol/L.");
        req.setSoapAssessment("Primary Essential Hypertension (Stage 1-2) with coexisting hypercholesterolemia. Elevated cardiovascular risk warranting combined antihypertensive and lipid-lowering therapy.");
        req.setSoapPlan("1. Commence Ramipril 5mg PO daily and Atorvastatin 20mg PO at night.\n2. Low-sodium Mediterranean dietary guidance provided (<2g sodium/day).\n3. Keep 4-week home blood pressure log (morning and evening readings).\n4. Repeat renal function (U&Es, eGFR) and liver profile in 6 weeks.\n5. Cardiology clinic review in 8 weeks.");
    }

    private void populateEndocrinologyVisit(PatientVisitRequest req, int pIdx) {
        req.setAttendingDoctor("Dr. Rajesh Patel");
        req.setDepartment("Endocrinology & Diabetes Clinic");
        req.setBloodPressure((128 + (pIdx % 10)) + "/" + (80 + (pIdx % 6)) + " mmHg");
        req.setHeartRate(72 + (pIdx % 8));
        req.setRespiratoryRate("16 breaths/min");
        req.setTemperature("36.6 °C");
        req.setOxygenSaturation("99%");
        req.setPainScore(0);

        req.setChiefComplaint("6-month metabolic follow-up and glycemic optimization");
        req.setDiagnosis("E11.9 - Type 2 diabetes mellitus without complications — Stable Glycemic Control");
        req.setPrescriptions("Metformin 1000mg PO BD (with meals), Empagliflozin 10mg PO OD (morning)");
        req.setMedicalNotes("Glycated hemoglobin (HbA1c) 7.1% (54 mmol/mol). Annual diabetic foot exam and microalbuminuria screening normal.");

        req.setSoapSubjective("Patient attends for 6-month diabetic review. Reports strict compliance with oral anti-hyperglycemic agents and home glucose monitoring. Occasional post-prandial glucose peaks up to 9.2 mmol/L. Denies hypoglycemic episodes, visual blurring, polydipsia, polyuria, or peripheral paresthesias.");
        req.setSoapObjective("BMI calculated within clinical range. Thyroid gland palpably normal without nodularity. Lower extremity monofilament sensory testing intact 10/10 bilaterally. Dorsalis pedis and posterior tibial pulses strong. Lab values: HbA1c 7.1%, fasting glucose 7.2 mmol/L, serum creatinine 78 umol/L, eGFR >90 mL/min/1.73m2, urine albumin-to-creatinine ratio (ACR) normal (<2.5 mg/mmol).");
        req.setSoapAssessment("Type 2 Diabetes Mellitus with satisfactory intermediate glycemic control. No evidence of microvascular target organ damage.");
        req.setSoapPlan("1. Continue Metformin 1000mg BD; initiate Empagliflozin 10mg OD for enhanced cardio-renal protection.\n2. Reinforce low glycemic index dietary patterns and 150 min/week moderate physical activity.\n3. Scheduled for NHS Diabetic Eye Screening Programme (DESP) digital retinal photography.\n4. Repeat HbA1c, lipid panel, and urine ACR in 6 months.");
    }

    private void populateRespiratoryVisit(PatientVisitRequest req, int pIdx) {
        req.setAttendingDoctor("Dr. Fiona Campbell");
        req.setDepartment("Respiratory Medicine Clinic");
        req.setBloodPressure((122 + (pIdx % 8)) + "/" + (76 + (pIdx % 6)) + " mmHg");
        req.setHeartRate(82 + (pIdx % 10));
        req.setRespiratoryRate("17 breaths/min");
        req.setTemperature("36.9 °C");
        req.setOxygenSaturation("98%");
        req.setPainScore(1);

        req.setChiefComplaint("Subacute wheezing and nocturnal dry cough following upper respiratory infection");
        req.setDiagnosis("J45.40 - Moderate persistent asthma with mild seasonal/post-viral exacerbation");
        req.setPrescriptions("Fostair (Beclometasone / Formoterol 100/6 mcg) 2 puffs BD, Salbutamol 100mcg MDI 2 puffs PRN");
        req.setMedicalNotes("Spirometry demonstrates post-bronchodilator FEV1 improvement of 14% (320 mL), confirming significant airway reversibility.");

        req.setSoapSubjective("Patient reports 3-week history of increased shortness of breath with exertion and dry nocturnal coughing, exacerbated by cold weather following a viral head cold. Utilizing rescue Salbutamol inhaler 4-5 times per week. Denies fever, purulent sputum production, hemoptysis, or pleuritic chest discomfort.");
        req.setSoapObjective("Respiratory rate 17/min, SpO2 98% on ambient air. Trachea central, symmetric chest wall expansion. Chest auscultation reveals end-expiratory polyphonic wheezes bilaterally throughout the mid and lower lung fields. No crackles or pleural friction rubs. Peak expiratory flow rate (PEFR) 390 L/min (78% of predicted).");
        req.setSoapAssessment("Moderate persistent asthma experiencing mild post-viral bronchospastic exacerbation secondary to suboptimal maintenance anti-inflammatory adherence.");
        req.setSoapPlan("1. Step up to combination ICS/LABA maintenance inhaler: Fostair 100/6 mcg 2 puffs twice daily via spacer.\n2. Continue Salbutamol 100mcg 2 puffs PRN for breakthrough symptoms.\n3. Issued individualized NHS Personal Asthma Action Plan (PAAP).\n4. Inhaler technique verified with aerochamber spacer.\n5. Repeat spirometry and clinical review in 8 weeks.");
    }

    private void populateNeurologyVisit(PatientVisitRequest req, int pIdx) {
        req.setAttendingDoctor("Dr. Edward Rochester");
        req.setDepartment("Neurology Department");
        req.setBloodPressure((124 + (pIdx % 10)) + "/" + (78 + (pIdx % 6)) + " mmHg");
        req.setHeartRate(72 + (pIdx % 8));
        req.setRespiratoryRate("15 breaths/min");
        req.setTemperature("36.7 °C");
        req.setOxygenSaturation("99%");
        req.setPainScore(4);

        req.setChiefComplaint("Recurrent throbbing unilateral hemicranial headaches with photophobia");
        req.setDiagnosis("G43.009 - Migraine without aura, not intractable — Frequent Episodic");
        req.setPrescriptions("Sumatriptan 50mg PO at onset (max 100mg/24h), Propranolol 40mg PO BD (prophylaxis), Naproxen 500mg PO PRN");
        req.setMedicalNotes("Complete neurological examination normal. Cranial nerves II-XII intact. Fundoscopy sharp, no papilledema.");

        req.setSoapSubjective("Patient describes a 6-month history of pulsating, severe left-sided temporal headaches occurring 4-6 times per month. Headaches typically last 12-24 hours if untreated and are accompanied by intense photophobia, phonophobia, and nausea. Identifies stress and sleep disruption as prominent precipitants.");
        req.setSoapObjective("Alert and oriented x3. Fundoscopy demonstrates crisp optic disc margins with spontaneous venous pulsations. Cranial nerves II through XII intact. Motor strength 5/5 in all muscle groups. Deep tendon reflexes 2+ and symmetric. Sensory exam intact to light touch and pinprick. Gait normal, negative Romberg sign.");
        req.setSoapAssessment("Episodic Migraine without Aura (ICHD-3 criteria met). High symptom burden with significant functional impairment warranting acute triptan therapy and oral beta-blocker prophylaxis.");
        req.setSoapPlan("1. Acute therapy: Sumatriptan 50mg PO at earliest headache onset with Naproxen 500mg PO with food.\n2. Prophylaxis: Propranolol 40mg PO BD to reduce migraine frequency.\n3. Maintain daily headache and trigger diary (MIDAS score tracking).\n4. Follow-up consultation in 12 weeks to assess prophylactic efficacy.");
    }

    private void populateOrthopedicsVisit(PatientVisitRequest req, int pIdx) {
        req.setAttendingDoctor("Dr. Tariq Mansoor");
        req.setDepartment("Orthopedic Surgery & Musculoskeletal Clinic");
        req.setBloodPressure((130 + (pIdx % 10)) + "/" + (82 + (pIdx % 6)) + " mmHg");
        req.setHeartRate(74 + (pIdx % 8));
        req.setRespiratoryRate("16 breaths/min");
        req.setTemperature("36.8 °C");
        req.setOxygenSaturation("99%");
        req.setPainScore(5);

        req.setChiefComplaint("Progressive joint pain and morning stiffness in the right knee upon weight bearing");
        req.setDiagnosis("M17.11 - Unilateral primary osteoarthritis, right knee (Kellgren-Lawrence Grade 2-3)");
        req.setPrescriptions("Ketoprofen 2.5% topical gel applied TDS, Paracetamol 1000mg PO QDS PRN");
        req.setMedicalNotes("Weight-bearing bilateral knee radiographs show moderate medial joint space narrowing and subchondral sclerosis.");

        req.setSoapSubjective("Patient reports an 8-month history of insidious, aching right knee pain exacerbated by prolonged walking, standing, and negotiating stairs. Morning joint stiffness lasts 15-20 minutes. Notes intermittent joint crepitus. Denies true mechanical locking, giving way, or hot erythema.");
        req.setSoapObjective("Mild antalgic gait favoring the right lower limb. Right knee: mild joint effusion, marked tenderness along the medial joint line. Range of motion: active flexion 115 degrees (contralateral 130 degrees), extension lacking 5 degrees. Ligamentous testing (anterior/posterior drawer, Lachman, varus/valgus stress) stable. McMurray test negative for acute meniscal tear.");
        req.setSoapAssessment("Primary Osteoarthritis of the Right Knee (moderate severity) with functional mobility limitation and mechanical pain.");
        req.setSoapPlan("1. Referral to NHS Musculoskeletal Physiotherapy for quadriceps strengthening and biomechanical gait optimization.\n2. Prescribed topical NSAID gel and PRN oral analgesia.\n3. Recommended low-impact aerobic exercise (swimming, stationary cycling) and shock-absorbing footwear.\n4. Orthopedic clinic review in 12 weeks; discuss intra-articular hyaluronic acid / corticosteroid injection if conservative measures plateau.");
    }

    private void populateDermatologyVisit(PatientVisitRequest req, int pIdx) {
        req.setAttendingDoctor("Dr. Sophie Laurent");
        req.setDepartment("Dermatology Clinic");
        req.setBloodPressure((120 + (pIdx % 8)) + "/" + (76 + (pIdx % 6)) + " mmHg");
        req.setHeartRate(70 + (pIdx % 8));
        req.setRespiratoryRate("15 breaths/min");
        req.setTemperature("36.7 °C");
        req.setOxygenSaturation("100%");
        req.setPainScore(2);

        req.setChiefComplaint("Erythematous, pruritic cutaneous eruption affecting flexural creases");
        req.setDiagnosis("L20.9 - Atopic dermatitis, unspecified — Active Flexural Flare");
        req.setPrescriptions("Betamethasone Valerate 0.1% cream (Betnovate) OD for 14 days, Epaderm Ointment QDS liberal emollient, Cetirizine 10mg PO nocte");
        req.setMedicalNotes("High-resolution dermoscopy of atypical nevi performed: all benign melanocytic patterns. Cutaneous barrier repair initiated.");

        req.setSoapSubjective("Patient presents with a 4-week history of itchy, dry, inflamed red patches over bilateral antecubital fossae, popliteal fossae, and posterior neck. Pruritus is intense and significantly disrupts sleep. Patient has been applying over-the-counter aqueous cream with insufficient relief.");
        req.setSoapObjective("Cutaneous exam reveals poorly demarcated erythematous, lichenified plaques with superficial excoriations across bilateral antecubital and popliteal creases. No purulent exudate, golden crusting, or satellite pustules (no secondary impetiginization). Nails show no pitting or onycholysis. Dermoscopic survey of pigmented lesions unremarkable.");
        req.setSoapAssessment("Moderate Atopic Eczema (EASI Score: 12.4) with flexural lichenification and acute pruritic flare.");
        req.setSoapPlan("1. Potent topical corticosteroid: Betamethasone Valerate 0.1% cream applied thinly once daily for 14 days, tapering to Hydrocortisone 1% maintenance.\n2. Liberal emollient therapy: Epaderm Ointment applied generously at least 4 times daily and used as soap substitute.\n3. Cetirizine 10mg PO at night for nocturnal pruritus.\n4. Avoid soap, hot showers, and synthetic fabrics.\n5. Dermatology review in 6 weeks.");
    }

    private void populateGastroenterologyVisit(PatientVisitRequest req, int pIdx) {
        req.setAttendingDoctor("Dr. David MacLeod");
        req.setDepartment("Gastroenterology & Digestive Health");
        req.setBloodPressure((126 + (pIdx % 10)) + "/" + (80 + (pIdx % 6)) + " mmHg");
        req.setHeartRate(72 + (pIdx % 8));
        req.setRespiratoryRate("15 breaths/min");
        req.setTemperature("36.8 °C");
        req.setOxygenSaturation("99%");
        req.setPainScore(2);

        req.setChiefComplaint("Frequent retrosternal burning discomfort and post-prandial acid regurgitation");
        req.setDiagnosis("K21.00 - Gastro-esophageal reflux disease with esophagitis, without bleeding");
        req.setPrescriptions("Omeprazole 20mg PO OD (30 mins before breakfast for 8 weeks), Gaviscon Advance 10ml PO QDS PRN");
        req.setMedicalNotes("Abdominal ultrasound performed: mild diffuse hepatic steatosis, normal biliary tree without gallstones or ductal dilatation.");

        req.setSoapSubjective("Patient complains of a 3-month history of burning substernal discomfort occurring 3-4 times per week, typically 45 minutes after heavy evening meals and when lying flat. Reports intermittent sour acid regurgitation and post-prandial bloating. Denies dysphagia, odynophagia, hematemesis, melena, or unintended weight loss.");
        req.setSoapObjective("Abdomen soft, non-distended. Mild epigastric tenderness on deep palpation; no guarding, rebound tenderness, or organomegaly. Normal normoactive bowel sounds across all 4 quadrants. Liver edge smooth and non-tender. Full blood count normal (no anemia); Helicobacter pylori stool antigen test negative.");
        req.setSoapAssessment("Gastro-esophageal Reflux Disease (GERD) with mild reflux esophagitis. Absence of red-flag symptoms (dysphagia, weight loss, gastrointestinal bleeding).");
        req.setSoapPlan("1. Prescribed proton pump inhibitor: Omeprazole 20mg PO once daily taken 30 minutes before morning breakfast for 8 weeks.\n2. Gaviscon Advance liquid 10ml after meals and at bedtime PRN for breakthrough symptoms.\n3. Lifestyle guidance: elevate head of bed by 15cm, avoid late meals within 3 hours of sleep, reduce dietary caffeine/fat intake.\n4. Review in Gastroenterology clinic in 8 weeks; consider diagnostic upper GI endoscopy if symptoms fail to resolve.");
    }

    private void populateRheumatologyVisit(PatientVisitRequest req, int pIdx) {
        req.setAttendingDoctor("Dr. Megan Davies");
        req.setDepartment("Rheumatology Clinic");
        req.setBloodPressure((128 + (pIdx % 10)) + "/" + (82 + (pIdx % 6)) + " mmHg");
        req.setHeartRate(76 + (pIdx % 8));
        req.setRespiratoryRate("16 breaths/min");
        req.setTemperature("36.9 °C");
        req.setOxygenSaturation("99%");
        req.setPainScore(5);

        req.setChiefComplaint("Symmetric small joint pain, swelling, and morning stiffness in both hands");
        req.setDiagnosis("M06.9 - Rheumatoid arthritis, unspecified — Early Seropositive (DAS28: 4.4)");
        req.setPrescriptions("Methotrexate 15mg PO once weekly, Folic Acid 5mg PO once weekly (48h post-MTX), Prednisolone 15mg PO OD tapering over 6 weeks");
        req.setMedicalNotes("Serology confirmed positive: Anti-CCP >100 U/mL, Rheumatoid Factor 64 IU/mL, CRP 24 mg/L. Baseline chest radiograph clear.");

        req.setSoapSubjective("Patient presents with a 4-month history of progressive pain and puffy swelling in the bilateral metacarpophalangeal (MCP) and proximal interphalangeal (PIP) joints of both hands. Morning joint stiffness persists for >60 minutes, improving slowly through the day. Complains of pronounced generalized fatigue.");
        req.setSoapObjective("Hand examination demonstrates palpable boggy synovitis and tenderness across the 2nd, 3rd, and 4th MCP joints bilaterally, as well as the right 3rd PIP joint. Bilateral grip strength diminished. No rheumatoid nodules or periungual infarcts. Lab results: ESR 38 mm/hr, CRP 24 mg/L, Anti-CCP positive (>100 U/mL), RF elevated (64 IU/mL). Normal baseline LFTs and renal function.");
        req.setSoapAssessment("Early Seropositive Rheumatoid Arthritis with moderate disease activity (DAS28-CRP: 4.4). Immediate DMARD initiation indicated to prevent structural joint erosion.");
        req.setSoapPlan("1. Initiate Disease-Modifying Anti-Rheumatic Drug (DMARD): Methotrexate 15mg PO once weekly alongside Folic Acid 5mg weekly.\n2. Bridging oral corticosteroid: Prednisolone 15mg daily tapering by 2.5mg every 7 days.\n3. Arrange 4-weekly full blood count and liver function monitoring via shared care protocol.\n4. Scheduled clinic review in 6 weeks with DAS28 recalculation.");
    }

    private void populatePsychiatryVisit(PatientVisitRequest req, int pIdx) {
        req.setAttendingDoctor("Dr. Ciaran O'Reilly");
        req.setDepartment("Psychiatry & Behavioral Health");
        req.setBloodPressure((128 + (pIdx % 10)) + "/" + (82 + (pIdx % 6)) + " mmHg");
        req.setHeartRate(82 + (pIdx % 12));
        req.setRespiratoryRate("16 breaths/min");
        req.setTemperature("36.7 °C");
        req.setOxygenSaturation("99%");
        req.setPainScore(0);

        req.setChiefComplaint("Persistent excessive worry, somatic tension, and severe sleep onset insomnia");
        req.setDiagnosis("F41.1 - Generalized anxiety disorder with secondary psychophysiological insomnia");
        req.setPrescriptions("Sertraline 50mg PO OD (morning), Melatonin 2mg PO PRN nocte");
        req.setMedicalNotes("Mental Status Examination completed: cooperative, speech normal, affect anxious, no suicidal ideation or perceptual disturbances. GAD-7: 15/21, PHQ-9: 9/27.");

        req.setSoapSubjective("Patient reports an 8-month history of constant, uncontrollable worry centered around occupational responsibilities, family health, and financial security. Experiences marked somatic tension, restlessness, irritability, and middle-of-the-night awakenings. Denies panic attacks, obsessive-compulsive rituals, depressive anhedonia, or suicidal/self-harm ideation.");
        req.setSoapObjective("Mental Status Exam: Neat, well-groomed appearance. Cooperative with examiner, slight fidgeting and motor tension observed. Speech coherent, normal rate and prosody. Mood: 'nervous and stressed'; affect: mood-congruent, anxious, full range. Thought process logical and goal-directed without loose associations. Cognition intact, insight and judgment fully preserved.");
        req.setSoapAssessment("Generalized Anxiety Disorder (GAD-7: 15 - Severe Anxiety Range) with comorbid sleep disruption. Good prognosis with combined pharmacotherapy and cognitive behavioral interventions.");
        req.setSoapPlan("1. Commence selective serotonin reuptake inhibitor (SSRI): Sertraline 50mg PO once daily in the morning.\n2. Referral submitted for NHS Talking Therapies (Cognitive Behavioral Therapy - CBT for generalized anxiety).\n3. Provided sleep hygiene protocol and progressive muscle relaxation resources.\n4. Scheduled telephone check-in at 2 weeks to assess SSRI tolerability; psychiatric clinic review in 4 weeks.");
    }

    // ── 4. ATTACHMENT SPECS & MOCK PDF GENERATOR ─────────────────────────────

    public List<AttachmentSpec> getAttachmentSpecsForVisit(int visitIndex, String patientName, String mrn) {
        List<AttachmentSpec> attachments = new ArrayList<>();
        switch (visitIndex) {
            case 1 -> attachments.add(new AttachmentSpec(
                    "Cardiology_12_Lead_ECG.pdf",
                    "12-Lead Electrocardiogram (ECG) Report",
                    "Sinus Rhythm, Normal Axis, Rare Unifocal PVCs, QTc 418ms (Normal)"
            ));
            case 2 -> attachments.add(new AttachmentSpec(
                    "Endocrinology_HbA1c_Metabolic_Panel.pdf",
                    "Comprehensive Glycemic & Renal Panel",
                    "HbA1c: 7.1% (54 mmol/mol), Fasting Glucose: 7.2 mmol/L, eGFR: >90 mL/min"
            ));
            case 3 -> attachments.add(new AttachmentSpec(
                    "Pulmonary_Spirometry_Report.pdf",
                    "Diagnostic Spirometry & Flow-Volume Loop",
                    "Pre-FEV1: 2.85L (72%), Post-FEV1: 3.25L (+14% Reversible), FEV1/FVC: 76%"
            ));
            case 5 -> attachments.add(new AttachmentSpec(
                    "Orthopedic_Radiology_XRay.pdf",
                    "Weight-Bearing Knee Radiograph Summary",
                    "Medial joint space narrowing Grade 2-3, Subchondral sclerosis, No fracture"
            ));
            case 7 -> attachments.add(new AttachmentSpec(
                    "Gastro_Abdominal_Ultrasound.pdf",
                    "Upper Abdominal Diagnostic Ultrasound",
                    "Mild diffuse hepatic steatosis, Gallbladder and biliary tree unremarkable"
            ));
            case 8 -> attachments.add(new AttachmentSpec(
                    "Rheumatology_Autoantibody_Panel.pdf",
                    "Serological Autoantibody & Inflammatory Panel",
                    "Anti-CCP: >100 U/mL (Positive), RF: 64 IU/mL (Positive), CRP: 24 mg/L"
            ));
        }
        return attachments;
    }

    public byte[] generateSamplePdfContent(String patientName, String mrn, String documentTitle, String reportDetails) {
        String mockPdfHeader = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n3 0 obj\n<< /Type /Page /Parent 2 0 R /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>\nendobj\n4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n5 0 obj\n<< /Length 300 >>\nstream\nBT\n/F1 14 Tf\n50 750 Td\n(CRYPTOSHRED HEALTH EHR MEDICAL REPORT) Tj\n0 -30 Td\n(Patient: " + escapePdfString(patientName) + ") Tj\n0 -20 Td\n(MRN: " + escapePdfString(mrn) + ") Tj\n0 -30 Td\n(" + escapePdfString(documentTitle) + ") Tj\n0 -20 Td\n(" + escapePdfString(reportDetails) + ") Tj\nET\nendstream\nendobj\nxref\n0 6\n0000000000 65535 f \n0000000009 00000 n \n0000000058 00000 n \n0000000115 00000 n \n0000000220 00000 n \n0000000293 00000 n \ntrailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n550\n%%EOF";
        return mockPdfHeader.getBytes();
    }

    private String escapePdfString(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }
}
