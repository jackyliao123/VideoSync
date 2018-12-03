public class Packet {
	public int id;
	public byte[] content;
	public Packet() {
	}
	public Packet(int id, byte[] content) {
		this.id = id;
		this.content = content;
	}
}
