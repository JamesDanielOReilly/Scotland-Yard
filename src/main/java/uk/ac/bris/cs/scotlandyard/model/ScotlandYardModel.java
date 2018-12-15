package uk.ac.bris.cs.scotlandyard.model;

import static java.util.Objects.requireNonNull;
import static uk.ac.bris.cs.scotlandyard.model.Colour.Black;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import uk.ac.bris.cs.gamekit.graph.Edge;
import uk.ac.bris.cs.gamekit.graph.Graph;
import uk.ac.bris.cs.gamekit.graph.ImmutableGraph;

public class ScotlandYardModel implements ScotlandYardGame, Consumer<Move> {

	private List<Boolean> rounds;
	private Graph<Integer, Transport> graph;
	private List<Colour> colours = new CopyOnWriteArrayList<>();
	private List<ScotlandYardPlayer> players = new CopyOnWriteArrayList<>();
	private List<Spectator> spectators = new ArrayList<>();
	private int currentRound, currentPlayer, lastKnownMrX;
	private boolean gameOver;

	public ScotlandYardModel(List<Boolean> rounds, Graph<Integer, Transport> graph,
							 PlayerConfiguration mrX, PlayerConfiguration firstDetective,
							 PlayerConfiguration... restOfTheDetectives) {

		gameOver = false;
		this.rounds = Objects.requireNonNull(rounds);
		this.graph = Objects.requireNonNull(graph);
		this.currentRound = 0;

		if (rounds.isEmpty()) throw new IllegalArgumentException("Empty rounds");
		if (graph.isEmpty()) throw new IllegalArgumentException("Empty graph");
		if (mrX.colour != Black) throw new IllegalArgumentException("MrX should be Black");

		ArrayList<PlayerConfiguration> configurations = new ArrayList<>();
		for (PlayerConfiguration configuration : restOfTheDetectives)
			configurations.add(requireNonNull(configuration));
		configurations.add(0, requireNonNull(firstDetective));
		configurations.add(0, requireNonNull(mrX));

		List<Integer> locations = new ArrayList<>();
		for (PlayerConfiguration configuration : configurations) {
			if (locations.contains(configuration.location))
				throw new IllegalArgumentException("Duplicate location");
			locations.add(configuration.location);
			if (colours.contains(configuration.colour))
				throw new IllegalArgumentException("Duplicate colour");
			colours.add(configuration.colour);
			for (Ticket ticket : Ticket.values()) {
				if (!configuration.tickets.containsKey(ticket))
					throw new IllegalArgumentException("Players don't have each ticket type");
			}
			players.add(new ScotlandYardPlayer(configuration.player, configuration.colour, configuration.location, configuration.tickets));
		}
		for (ScotlandYardPlayer player : players) {
			if (player.isDetective()) {
				if (player.hasTickets(Ticket.Double) || player.hasTickets(Ticket.Secret)) {
					throw new IllegalArgumentException("Detectives can't have secret or double tickets");
				}
			}
		}
		isGameOver();
	}

	private Set<TicketMove> moveGenerator(Colour colour, Integer location){ //Adds all detective valid moves. Adds all of MrX's valid moves except for double moves.
		Set<TicketMove> generatedMoves = new HashSet<>(); //a list of generated moves
		Set<Integer> locations = new HashSet<>(); //locations of all of the detectives.
		for(ScotlandYardPlayer player: players){
			if(!player.isMrX()) {
				locations.add(player.location());
			}
		}
		Collection<Edge<Integer, Transport>> possibleEdges = graph.getEdgesFrom(graph.getNode(location)); //generates a list of possibleEdges from the given node.
		for(Edge<Integer, Transport> edge: possibleEdges) {
			if(players.get(colours.indexOf(colour)).hasTickets(Ticket.fromTransport(edge.data())) && !locations.contains(edge.destination().value())) {
				generatedMoves.add(new TicketMove(colour, Ticket.fromTransport(edge.data()), edge.destination().value()));
			}
			if(colour == Black && players.get(colours.indexOf(colour)).hasTickets(Ticket.Secret) && !locations.contains(edge.destination().value())) {
					generatedMoves.add(new TicketMove(Black, Ticket.Secret, edge.destination().value()));
				}
		} return generatedMoves;
	}

	private Set<Move> validMoves(Colour colour) {
		Set<Move> moves = new HashSet<>();
		Set<TicketMove> firstMoves = new HashSet<>();
		if (colour == Black) {
			firstMoves.addAll(moveGenerator(Black, players.get(0).location()));
			moves.addAll(firstMoves);
			if(currentRound != rounds.size()-1 && players.get(0).hasTickets(Ticket.Double)) { //Checks to see if it's not the last round and that the player a double ticket.
				for (TicketMove first : firstMoves) {
					Set<TicketMove> secondMoves = new HashSet<>();
					secondMoves.addAll(moveGenerator(Black, first.destination()));
					for (TicketMove second : secondMoves) {
						if (first.ticket() == second.ticket()) {
							if (players.get(0).hasTickets(first.ticket(), 2)) {
								moves.add(new DoubleMove(Black, first, second));
							}
						} else if (players.get(0).hasTickets(second.ticket()) && players.get(0).hasTickets(first.ticket())) {
							moves.add(new DoubleMove(Black, first, second));
						}
					}
				}
			}
		} else {
			moves.addAll(moveGenerator(colour, players.get(colours.indexOf(colour)).location()));
			if (moves.isEmpty()) {
				moves.add(new PassMove(colour));
			}
		}
		return Collections.unmodifiableSet(moves);
	}

	@Override
	public void registerSpectator(Spectator spectator) {
	    if (!spectators.contains(spectator)) {
            spectators.add(Objects.requireNonNull(spectator));
        } else throw new IllegalArgumentException("Spectator already exists");
	}

	@Override
	public void unregisterSpectator(Spectator spectator) {
		if (spectators.contains(spectator)) {
            spectators.remove(Objects.requireNonNull(spectator));
        } else if (spectator == null) { //
		    throw new NullPointerException("Cannot unregister a null spectator"); //
        } else throw new IllegalArgumentException("Spectator doesn't exist"); //
	}

	@Override
	public Collection<Spectator> getSpectators() {
		return Collections.unmodifiableCollection(spectators);
	}

	@Override
	public void startRotate() { //starts a new rotation if the game is not over.
		if(!isGameOver()) {
			currentPlayer = 0;
			players.get(0).player().makeMove(this, players.get(0).location(), validMoves(Black), this);
		} else throw new IllegalStateException("Game is Over");
	}

	private void MrXLocation() {

		if (rounds.get(currentRound)) {
			newMrXLocation();
		}
	}

	private int doubleLocation(MoveViewer visitor) {

		if (rounds.get(currentRound+1)) {
			return visitor.finalDestination;
		} else return lastKnownMrX;
	}

	private void newMrXLocation() {
			lastKnownMrX = players.get(0).location();
	}

	@Override
	public void accept(Move move) {
		if (validMoves(move.colour()).contains(move)) {
			MoveViewer visitor = new MoveViewer();
			move.visit(visitor);
			if (currentPlayer == 0) {
				if (visitor.isDoubleMove) {
					players.get(0).removeTicket(visitor.firstTicket);
					players.get(0).removeTicket(visitor.secondTicket);
					players.get(0).removeTicket(Ticket.Double);
					players.get(0).location(visitor.destination);
					MrXLocation();
					for (Spectator spectator : spectators) {
							spectator.onMoveMade(this, new DoubleMove(Black, visitor.firstTicket, lastKnownMrX, visitor.secondTicket, doubleLocation(visitor)));
					}
					currentRound++;
					for(Spectator spectator : spectators) {
						spectator.onRoundStarted(this, currentRound);
							spectator.onMoveMade(this, new TicketMove(Black, visitor.firstTicket, lastKnownMrX));
					}
					players.get(0).location(visitor.finalDestination);
					MrXLocation();
					currentRound++;
					for (Spectator spectator : spectators) {
						spectator.onRoundStarted(this, currentRound);
							spectator.onMoveMade(this, new TicketMove(Black, visitor.secondTicket, lastKnownMrX));
					}
				} else {
					players.get(0).removeTicket(visitor.ticket);
					players.get(0).location(visitor.destination);
					MrXLocation();
					currentRound++;
					for(Spectator spectator : spectators) {
						spectator.onRoundStarted(this, currentRound);
						spectator.onMoveMade(this, new TicketMove(Black, visitor.ticket, lastKnownMrX));
					}
				}
			} else {
				if (!visitor.isPassMove) {
					players.get(currentPlayer).removeTicket(visitor.ticket);
					players.get(0).addTicket((visitor.ticket));
					for(Spectator spectator : spectators) {
						spectator.onMoveMade(this, move);
					}
					players.get(currentPlayer).location(visitor.destination);
				} else {
					for (Spectator spectator : spectators) {
						spectator.onMoveMade(this, move);
					}
				}
			}
			currentPlayer++;
			if (currentPlayer != players.size() && !testIfCaptured()) {
				players.get(currentPlayer).player().makeMove(this, getPlayerLocation(colours.get(currentPlayer)), validMoves(colours.get(currentPlayer)), this);
			} else if (isGameOver()) {
				for (Spectator spectator : spectators) {
					spectator.onGameOver(this, getWinningPlayers());
				}
			} else {
				for (Spectator spectator : spectators) {
					spectator.onRotationComplete(this);
				}
			}
		} else throw new IllegalArgumentException("Move is not valid");
	}

	@Override
	public List<Colour> getPlayers() {
		return Collections.unmodifiableList(colours);
	}

	@Override
	public Set<Colour> getWinningPlayers() {
		Set<Colour> winners = new HashSet<>();
		if (testIfCaptured() || testIfStuck()) {
			winners.addAll(colours);
			winners.remove(Black);
		} else if (testDetectivesStuck() || currentRound == rounds.size()) {
			winners.add(Black);
		}
		return Collections.unmodifiableSet(winners);
	}

	@Override
	public int getPlayerLocation(Colour colour) {
		if (colour == Black) {
			if (isRevealRound()) {
				lastKnownMrX = players.get(0).location();
				return lastKnownMrX;
			} else return lastKnownMrX;
		} else return players.get(colours.indexOf(colour)).location();
	}

	@Override
	public int getPlayerTickets(Colour colour, Ticket ticket) {
		return players.get(colours.indexOf(colour)).tickets().get(ticket);
	}

	private boolean testIfCaptured() {
		for (ScotlandYardPlayer player : players) {
			if (player.location() == players.get(0).location() && !player.isMrX()) {
				return true;
			}
		}
		return false;
	}

	private boolean testDetectivesStuck() {
		for (ScotlandYardPlayer player : players) {
			if (!player.isMrX() && !moveGenerator(colours.get(players.indexOf(player)), player.location()).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private boolean testIfStuck() {
		return validMoves(Black).isEmpty();
	}

	@Override
	public boolean isGameOver() {
		gameOver = testIfCaptured() || testDetectivesStuck() || testIfStuck() || currentRound == rounds.size();
		return gameOver;
	}

	@Override
	public Colour getCurrentPlayer() {
		return colours.get(currentPlayer);
	}

	@Override
	public int getCurrentRound() {
		return currentRound;
	}

	@Override
	public boolean isRevealRound() {
		if (currentRound == 0) {

			return false;

		} else return rounds.get(currentRound - 1); //
	}

	@Override
	public List<Boolean> getRounds() {

		return Collections.unmodifiableList(rounds);
	}

	@Override
	public Graph<Integer, Transport> getGraph() {
		return new ImmutableGraph<>(graph);
	}
}