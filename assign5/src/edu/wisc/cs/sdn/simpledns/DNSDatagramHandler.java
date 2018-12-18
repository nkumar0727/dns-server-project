package edu.wisc.cs.sdn.simpledns;

import edu.wisc.cs.sdn.simpledns.packet.DNS;
import edu.wisc.cs.sdn.simpledns.packet.DNSQuestion;
import edu.wisc.cs.sdn.simpledns.packet.DNSResourceRecord;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

class DNSDatagramHandler {

    static short extractRequestTypeFromDNSHeader(final DNS dnsHeader) {
        return dnsHeader.getQuestions().get(0).getType();
    }

    static DNS extractDNSHeaderFromDatagramPacket(final DatagramPacket packet) {
        return DNS.deserialize(packet.getData(), packet.getLength());
    }

    static DatagramPacket convertDNSHeaderToDatagramPacket(final DNS dnsHeader,
                                                           final InetAddress destinationIPAddress,
                                                           final int destinationPort) {

        final byte[] buffer = dnsHeader.serialize();
        return new DatagramPacket(buffer, buffer.length, destinationIPAddress, destinationPort);
    }

    private static boolean areQuestionsValidTypes(final List<DNSQuestion> questionList) {

        short type;
        for (DNSQuestion question : questionList) {
            type = question.getType();
            if (type != DNS.TYPE_A && type != DNS.TYPE_AAAA &&
                    type != DNS.TYPE_CNAME && type != DNS.TYPE_NS) {
                return false;
            }
        }

        return true;
    }

    static boolean canHandleDNSRequest(final DNS dnsRequest) {
        return dnsRequest.getOpcode() == 0 && dnsRequest.isQuery()
                && !dnsRequest.getQuestions().isEmpty()
                && areQuestionsValidTypes(dnsRequest.getQuestions());
    }

    static boolean dnsResponseContains_CNAMERecord(final DNS dnsResponse) {
        for (DNSResourceRecord dnsResourceRecord : dnsResponse.getAnswers()) {
            if (dnsResourceRecord.getType() == DNS.TYPE_CNAME) {
                return true;
            }
        }

        return false;
    }

    static DNS generateDNSResponse(final DNS dnsRequest, final List<DNSResourceRecord> answerList) {
        final DNS dnsResponse = new DNS();

        dnsResponse.setId(dnsRequest.getId());
        dnsResponse.setAnswers(answerList);
        dnsResponse.setQuery(false);
        dnsResponse.setRcode(DNS.RCODE_NO_ERROR);
        dnsResponse.setRecursionDesired(false);
        dnsResponse.setRecursionAvailable(false);
        dnsResponse.setQuestions(dnsRequest.getQuestions());

        return dnsResponse;
    }

    static String findNextNameserverFromDNSResponse(final DNS dnsResponse) throws IllegalArgumentException {
        final DNSResourceRecord firstNameserverRecord = findFirstRecordInList(dnsResponse.getAuthorities(), DNS.TYPE_NS);
        return firstNameserverRecord.getData().toString();
    }

    static DNS generateDNSRequest(final DNSQuestion dnsQuestion, final short dnsId) {
        final DNS dnsRequest = new DNS();

        dnsRequest.setId(dnsId);
        dnsRequest.setOpcode(DNS.OPCODE_STANDARD_QUERY);
        dnsRequest.setQuery(true);
        dnsRequest.addQuestion(dnsQuestion);
        //dnsRequest.setRecursionDesired(true);

        return dnsRequest;
    }

    static InetAddress getNextCachedNameserver(final DNS dnsResponse) throws UnknownHostException {
        for (DNSResourceRecord authorityRecord : dnsResponse.getAuthorities()) {
            if (authorityRecord.getType() == DNS.TYPE_NS) {
                for (DNSResourceRecord additionalRecord : dnsResponse.getAdditional()) {
                    if (additionalRecord.getType() == DNS.TYPE_A &&
                            additionalRecord.getName().equals(authorityRecord.getData().toString())) {
                        return CIDRConverter.convertDottedDecimalStringToInetAddress(additionalRecord.getData().toString());
                    }
                }
            }
        }

        return null;
    }

    private static DNSResourceRecord findFirstRecordInList(final List<DNSResourceRecord> recordList,
                                                           final short type) throws IllegalArgumentException {

        for (DNSResourceRecord dnsResourceRecord : recordList) {
            if (dnsResourceRecord.getType() == type) {
                return dnsResourceRecord;
            }
        }

        throw new IllegalArgumentException("Could not find record of type "+type);
    }

    static String extractCNAMEAnswerFromDNSResponse(final DNS dnsResponse) throws NullPointerException {
        return dnsResponse.getAnswers().get(0).getData().toString();
    }
}
