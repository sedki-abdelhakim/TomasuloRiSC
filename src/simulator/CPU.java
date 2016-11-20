package simulator;

import java.util.Arrays;
import java.util.LinkedList;

import memory.Cache;

public class CPU {
	
	static enum functionalUnit{
		MULT, LOGICAL, ADD, BRANCH, LOAD, STORE
	}
	static enum Instruction{
		LW,SW,JMP,BEQ,JALR,RET,ADD,ADDI,SUB,NAND,MUL
	}

	int missPrediction = 0;
	int branchCount = 0;
	int pipelineWidth;
	int instructionBufferSize;
	java.util.Queue<int[]> instructionBuffer;
	int [] regFile = new int[8];
	int [] regTable = new int[8];
	RSEntry [][] reservationStation = new RSEntry[6][];
	int[] reservationStationLatencies = new int[6];
	Cache instructionCache;
	Cache dataCache;
	short PC;
	short oldPC;
	
	public CPU(Cache instructionCache, Cache dataCache,int ...unitsNo) {
		this.instructionCache=instructionCache;
		this.dataCache=dataCache;
		Arrays.fill(regTable, -1);
		int i = 0;
		for(int size : unitsNo){
			if(i<6)	reservationStation[i++] = new RSEntry [size];
			else reservationStationLatencies[i-6] = size;
		}
	}
	
	public int[] decode(int instruction){
		
		int opcode = (instruction&(7<<13))>>13;
		int functional= (instruction&(7));
		int [] decoded = null;
		switch(opcode){
		case 7:
			if(functional > 3) return null;
			
			decoded = new int [5];
			decoded[2] = (instruction&(7<<10))>>10;
			decoded[3] =(instruction&(7<<7))>>7;
			decoded[4] = (instruction&(7<<4))>>4;
			
			switch(functional){
			case 0:decoded[0] = functionalUnit.ADD.ordinal();
			       decoded[1] =Instruction.ADD.ordinal() ;
			break;
			case 1:decoded[0] = functionalUnit.ADD.ordinal();
		       decoded[1] =Instruction.SUB.ordinal() ;
		       break;
			case 2:decoded[0] = functionalUnit.LOGICAL.ordinal();
		       decoded[1] =Instruction.NAND.ordinal() ;
		       break;
			case 3:decoded[0] = functionalUnit.MULT.ordinal();
		       decoded[1] =Instruction.MUL.ordinal() ;
		       break;
				
			}
			case 0:
				decoded = new int [5];
				decoded[0]=functionalUnit.LOAD.ordinal();
				decoded[1] =Instruction.LW.ordinal() ;
				decoded[2] = (instruction&(7<<10))>>10;
				decoded[3] =(instruction&(7<<7))>>7;
				decoded[4] = (instruction&(127));break;
			case 1:
				decoded = new int [5];
				decoded[0]=functionalUnit.STORE.ordinal();
				decoded[1] =Instruction.SW.ordinal() ;
				decoded[2] = (instruction&(7<<10))>>10;
				decoded[3] =(instruction&(7<<7))>>7;
				decoded[4] = (instruction&(127));break;
			case 2:
				decoded = new int [5];
				decoded[0]=functionalUnit.BRANCH.ordinal();
				decoded[1] =Instruction.BEQ.ordinal() ;
				decoded[2] = (instruction&(7<<10))>>10;
				decoded[3] =(instruction&(7<<7))>>7;
				decoded[4] = (instruction&(127));break;
			case 3:
				decoded = new int [5];
				decoded[0]=functionalUnit.ADD.ordinal();
				decoded[1] =Instruction.ADDI.ordinal() ;
				decoded[2] = (instruction&(7<<10))>>10;
				decoded[3] =(instruction&(7<<7))>>7;
				decoded[4] = (instruction&(127));break;
			case 4:
				decoded = new int [4];
				decoded[0]=functionalUnit.BRANCH.ordinal();
				decoded[1] =Instruction.JMP.ordinal() ;
				decoded[2] = (instruction&(7<<10))>>10;
				decoded[3] =(instruction&(1023));
				break;

		case 5:decoded = new int [4];
				decoded[0]=functionalUnit.BRANCH.ordinal();
				decoded[1] =Instruction.JALR.ordinal() ;
				decoded[2] = (instruction&(7<<10))>>10;
				decoded[3] =(instruction&(7<<7))>>7;
				break;
		case 6:decoded = new int [3];
				decoded[0]=functionalUnit.BRANCH.ordinal();
					decoded[1] =Instruction.RET.ordinal() ;
					decoded[2] = (instruction&(7<<10))>>10;
					break;
			
			
		}
		return decoded;
	}
	
	static class RSEntry{
		String name;
		boolean busy;
		String op;
		int Vj, Vk, Qj, Qk, Dest, A, cyclesRemToWrite;
		int instrcutionIndex;
		int latency;

		public RSEntry(String name, String op, int vj, int vk, int qj, int qk, int dest, int a,
						int cyclesRemToWrite, int latency, int instructionIndex) {
			this.name = name;
			this.op = op;
			Vj = vj;
			Vk = vk;
			Qj = qj;
			Qk = qk;
			Dest = dest;
			A = a;
			this.latency = latency;
			this.cyclesRemToWrite = cyclesRemToWrite;
			this.instrcutionIndex=instructionIndex;
			busy=false;
			
		}
		public String toString(){
			return "<" + name + ", " + busy + ", " + op + ", " + Vj + ", " + Vk + ", " + Qj + ", " + Qk + ", " + Dest + ", " + A + ", " + cyclesRemToWrite + ">";
		} 
		
	}
	
	public void init(int pipelineWidth, int instructionBufferSize, short startAddress) {
		this.pipelineWidth = pipelineWidth;
		this.instructionBufferSize = instructionBufferSize;
		this.PC = startAddress;
		instructionBuffer = new LinkedList<int[]>();
		
		functionalUnit[] functionalUnitNames = functionalUnit.values();
		for(int i=0; i<6; i++) {
			for(int j=0; j<reservationStation[i].length; j++) {
				reservationStation[i][j] = new RSEntry(functionalUnitNames[i]+""+(j+1), 
						"", -1, -1, -1, -1, -1, -1, -1, reservationStationLatencies[i], -1);
			}
		}
	}
	
	public void simulate() {
		
		@SuppressWarnings("unused")
		int cycles = 0;
		
		while(true) {
			
			// FRONT END
			if((instructionBufferSize-instructionBuffer.size()) >= pipelineWidth) {
				for(int i=0; i<pipelineWidth; i++) {
					short fetchedInstruction = (short) instructionCache.read(PC, true);
					int[] decodedInstruction = this.decode(fetchedInstruction);
					instructionBuffer.offer(decodedInstruction);
					
					PC++;
					
					if(decodedInstruction[1]==Instruction.JMP.ordinal()) {
						PC = (short)(regFile[decodedInstruction[2]]);
						PC += decodedInstruction[3];
					} else if(decodedInstruction[1]==Instruction.JALR.ordinal()) {
						PC = (short) decodedInstruction[3];
					} else if(decodedInstruction[1]==Instruction.RET.ordinal()) {
						PC = (short) decodedInstruction[2];
					} else if(decodedInstruction[1]==Instruction.BEQ.ordinal()) {
						oldPC = PC;
						PC += (decodedInstruction[4]<0)?((short) decodedInstruction[4]):0;
					}
				}
			}
			
			// BACK END
			//boolean issue = 
			
			
		}
		
	}
	
	public static void main(String[] args) {
		System.out.println(Arrays.toString(Instruction.values()));
	}
}
