package uk.ac.bris.cs.scotlandyard.model;

/**
 * Created by Owner on 25/04/2017.
 */
public class MoveViewer implements MoveVisitor{

    public int destination, finalDestination;
    public Ticket ticket, firstTicket, secondTicket;
    public Boolean isPassMove = false;
    public Boolean isDoubleMove = false;

    public void visit(PassMove move) {
        isPassMove = true;
        isDoubleMove = false;
    }

    public void visit(TicketMove move) {
        destination = move.destination();
        ticket = move.ticket();
        isPassMove = false;
        isDoubleMove = false;
    }

    public void visit(DoubleMove move) {
        destination = move.firstMove().destination();
        finalDestination = move.finalDestination();
        firstTicket = move.firstMove().ticket();
        secondTicket = move.secondMove().ticket();
        isDoubleMove = true;
        isPassMove = false;
    }

}
