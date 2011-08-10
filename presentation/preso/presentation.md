!SLIDE title-page
.notes We're hiring
Typesafe Scala training with Heiko Seeberger and Josh Suereth at Chariot first week of October?
Any interest in a one-day seminar on Scala in the enterprise?  Scala & Spring, Scala & OSGi, Scala & GridGain, etc

## LMAX Disruptor

Jamie Allen

jallen@chariotsolutions.com

@jamie_allen

August 10, 2011

!SLIDE transition=fade
.notes The "virtual" nature of our runtime and deployment environment has desensitized us as developers to the impact of our design and implementation decisions
It flies in the face of popular concurrency abstractions to show what can be accomplished when implementing simple code in a highly-optimized fashion
This is a decidedly NOT functional implementation.  No referential transparency, lots of shared mutable state

# There is nothing new here

* 6 million TPS
* "Virtual" runtime and deployment environment
* Breaks popular concurrency abstractions
* Not functional?!? (OMG! ONOZ!)

!SLIDE transition=fade
.notes 	Initiative started several years ago at Betfair, with the Flywheel and 100x projects, trying to glean more performance from their system
Looked into J2EE, SEDA, Actors, etc - couldn't get the throughput they desired
SEDA: complex, event-driven application divided into stages connected by queues, supporting back pressure and load management
Actors are a subset of SEDA, with lock-free semantics
Neither project made it to production due to legacy integration issues

# How Did They Arrive at the Disruptor

* Betfair attempts the Flywheel and 100x projects
* Looked into J2EE, SEDA, Actors, etc

!SLIDE transition=fade
.notes Betfair spun off Tradefair into LMAX, Martin Thompson leaves Matt Youill, works with Mike Barker and the rest of the team on their clean slate, greenfield Disruptor implementation
So named because it has elements for dealing with graphs of dependencies comparable to the Java7 Phaser concurrency type, introduced in support of ForkJoin
And if there's a Phaser, there should be a Disruptor, right?
(TOBY) Disruptor serves the purpose of a queue in a SEDA architecture
But why implement it on the JVM instead of C++ or a native implementation?

# How Did They Arrive at the Disruptor

* Betfair begets Tradefair/LMAX
* LMAX implements with a clean slate

!SLIDE transition=fade

# Mechanical Sympathy

* Jackie Stewart - all great drivers must understand how their machine works to derive the fastest time driving it
* "The most amazing achievement of the computer software industry is its continuing cancellation of the steady and staggering gains made by the computer hardware industry." - Henry Peteroski, as quoted on Martin's blog

!SLIDE transition=fade
.notes Never release the core to the kernel (thus maintaining your L3 cache)
Avoid lock arbitration 
Minimize usage of memory barriers - they guarantee order, but also cache coherency
Pre-allocate and reuse sequential memory to avoid GC and compaction and enable cache pre-	  fetching
Thread control: In a low latency, high throughput system with balanced flow, you can assign a thread to a core from the JVM by never releasing control of the thread (wait, ForkJoin)

# Keys to the Disruptor's Performance

* Control the core
* Avoid lock arbitration 
* Minimize usage of memory barriers
* Pre-allocate and reuse memory
* Thread control

!SLIDE transition=fade
.notes Locks, extremely non-performant due to context switching in the kernel which suspend threads waiting on a lock until it is released
Note that during a kernel context switch, the OS may decide to perform other tasks not related to your process, thus losing valuable cycles
CAS semantics are much better, since no kernel context switch is required for lock arbitration, but the processor must still lock its instruction pipeline to ensure atomicity and introduce a memory barrier to ensure that changes are made visible to all threads
Memory barriers are used by processors to indicate sections of code where the ordering of memory updates matters - all memory changes appear in order at the point required (note: compilers can add software MBs in addition to the processor's, which is how Java's volatile keyword works)
Processors only need to guarantee that the execution of instructions gives the same result, regardless of order, and thus perform instructions out of order frequently to enhance performance
Memory barriers (volatile) specify where no optimizations can be performed to ensure that ordering is correct at runtime

# Avoid Locks

* Context switching is painful
* CAS semantics are much better, but no panacea
* Limited use of memory barriers are the key 

!SLIDE transition=fade
.notes Unbounded queues use linked lists, which are not contiguous and therefore do not support striding (prefetching cache lines)
Bounded queues use arrays, where the head (dequeuing) and tail (enqueuing) are the primary points of write contention, but also have a tendency to share a cache line
In Java, queues are also a significant source of garbage - the memory for data must be allocated, and if unbounded, the linked list node must also be allocated; when dereferenced, all of these objects must be reclaimed

# What's Wrong With Queues

* Unbounded linked lists don't "stride"
* Bounded arrays share cache lines
* Cause heavy GC

!SLIDE transition=fade
.notes What if you designed your system to utilize on-board caches for a core as effectively as possible?
Allocate a large ring buffer of byte arrays on startup, and copy data into those arrays as received/handled
Sequencing of data in main memory is also important - as the core understands your usage of data from memory, it can pre-load the cache with data it knows you need next.  You need to understand how you are allocating data and what that means (see padding)
Maximizing the use of "cache lines" (64 bytes, 8 longs of 8 bytes each, avoiding misses and "false sharing" - which aids with...

# Memory Allocation

* Cache is king
* Pre-allocate and reuse
* Not functional programming
* Sequence data
* Cache lines and "false sharing"

!SLIDE transition=fade
.notes Processors are much faster than memory now, and to optimize their performance, they use varying levels of caches (registers, store buffers, L1, L2, L3 and main memory) to support the execution of instructions, and they are kept coherent via message passing protocols
Speed of cache access is measured in cycles, but generally occurs in nanoseconds
Registers are obvious (on-processor storage for WIP)
Store buffers disambiguate memory access and manage dependencies for instructions (loads 	  and stores) occurring out of program order.  While a request for data to be stored to an L2 cache line is outstanding, the data is temporarily stored on one or more store buffers on the processor itself.  On an Intel CPU, you only get 4 at a time.  No loop should write to more than these 4 spaces at a time for maximum speed (write combining).  Split logic up so that separate loop iterations execute sequentially for more speed.
Note that store barriers are flushed when a memory barrier is hit - best to model a problem so that barriers are hit at the boundary of the work unit.  Change a variable last.
Caches are STATIC Random Access Memory (bistable latching circuitry), does not need to be periodically refreshed like DYNAMIC RAM used in main memory (charged capacitors "leak" the charge denoting whether the bit is 0 or 1, so data "fades").  DRAM is much simpler - one transister and a capacitor per bit versus 6 in SRAM, and thus much higher densities (hundreds of billions of transistors and capacitors  on a single memory chip).  There's even non-volatile SRAM which maintains data even when power is lost.
Note: Your fancy i7 processor has an 8MB on-die unified L3 cache that is inclusive, shared by all cores
Some processors have rules for the caches, such strictly inclusive, where all data in L1 must also be in L2.  Athlon processors are exclusive and can hold more data, which is great when L1 is comparable in size to L2, diminishes when L2 is many times larger.  Not universal, so you have to know your processors policy.
One of the most expensive operations for a process is a cache read miss - when data is looked for in one of the caches and not found, so it must allocate space (evicting something else) and go to the next level to retrieve the data (note: write misses have no penalty because the data can be copied in background)
The cache "hit rate" measures the effectiveness of your program/algorithm in using a cache

# Caching

* registers
* store buffers
* L1 (SRAM)
* L2 (SRAM)
* L3 (SRAM)
* Main memory (DRAM)

!SLIDE transition=fade
.notes Data is not moved in bytes or words, but in cache lines/cache blocks (32-256 bytes depending on the processor, usually 64) as buckets in the unchained hashmap
If two variables are on the same cache line and written to by different threads, they present the same problems of write contention as if they were one variable ("false sharing")
For performance, you must ensure that independent but concurrently written data are on separate cache lines.  PAD your data to make sure.

# Cache Lines

* Cache "misses" are expensive
* Variables shouldn't share a cache line

!SLIDE transition=fade
.notes Compulsory/Cold, happens the first time a datum is accessed, can be ameliorated through pre-fetching
Capacity, happens due to the finite space of a cache regardless of associativity or block size.  Just not there because there is no room, you're using a lot of data at once.  CPU caches almost always have every line filled, almost every allocation requires eviction
Conflict, avoidable if the cache had not evicted an entry earlier (a victim, which can also be cached in a "victim cache")
Mapping, due to degree of associativity of data (if data can go anywhere, as opposed to direct mapped where it must go in a specific memory space)
Replacement, due to eviction choice of the replacement policy such as LRU (perfect replacement policy: an oracle that looks into the future to find a cache entry which is actually not going to be hit)

# Cache Misses

* Compulsory/Cold
* Capacity
* Conflict
	* Mapping
	* Replacement

!SLIDE transition=fade
.notes When data is accessed from main memory in a predictable fashion (such as walking the data in a predictable "stride"), the processor can optimize by pre-fetching data it expects will be needed shortly to avoid "compulsory cache misses". This ring buffer has a predicatable pattern of access
Note that data structures such as linked lists and trees tend to have nodes that are more widely distributed (non-contiguous) in memory and therefore no predictable strides for performance optimization, which forces the processor to perform main memory direct access more often at the time the data is needed at significant performance cost

# Striding

* Predictable access begets pre-fetching
* Data structures must be contiguous

!SLIDE transition=fade
.notes The reuse of array buffers guarantees no GC of mature objects, thus no global GC compaction to "stop the world" - limit GC to short- and long-lived objects, not those in the middle
Latency occurs as those objects are copied between generations, which is minimized by reusing objects so that they only traverse the generations once
Restart the machine every day to clear the heap, guarantee no compaction (can go 3-4 days without worrying about it, but this is a safety measure)
How much time do we all waste trying to find the optimal GC strategy for a system?  
What if you were able to design your system so that you didn't have to think about it, because YOU CONTROL how much GC and compaction is taking place?

# Garbage Collection

* Reuse of memory prevents GC and compaction
* Restart every day to clear the heap

!SLIDE transition=fade
.notes Per Daniel Spiewak, was considered for a core data structure in Clojure before the bit-mapped vector trie was selected by Rich Hickey - FUNCTIONAL RING BUFFER WITH VECTOR STAMPS
The Ring Buffer is a bounded, pre-allocated data structure, and the allocated data elements will exist for the life of the Disruptor instance
Since the data is allocated all at once on startup, it is highly likely that the memory will be contiguous in main memory and will support effective striding for the caches
The "ring" characteristics of the array buffer promote resequencing - you never stop traversing the queue (sequence number increases, use MOD by ring size to get slot)
On most processors, there is a high cost for a remainder calculation on a sequence number which determines the slot in the ring, but it can be greatly reduced by making the ring size a power of 2; use a bit mask of ring size minus one to perform the remainder operation efficiently as compared to sequence number % size (600x faster?)
(TOBY) Networking software and devices have been using this technique for years - every networking card has been powered by a pair of ring buffers

# Implementation: Ring Buffer

* Bounded
* Pre-allocated all at once
* Re-traversed
* Bit mask modulus
* Your network card

<img src="ringbuffer.png" class="illustration" note="final slash needed"/>

!SLIDE transition=fade
.notes Used for network IO, file system reads, etc
Basic counter for single producer, atomic int/long for multiple producers (using CAS to protect the counter)
If more than one producer, they can race each other for slots and use CAS on the sequence number for next available slot to use
When an entry in the ring buffer is claimed by a producer, it copies data into one of the pre-allocated elements
This two-phase operation - getting the sequence number, copying the data and then explicitly committing to Producer Barrier, separates the action of putting the data into a slot and making it visible.  It also helps to maintain two-phase semantics if more than one Disruptor is accessed by a producer
Producers can check with Consumers to see where they are so they don't overwrite ring buffer slots still in use
ClaimStrategy: single threaded or multithreaded (CAS Atomici vars)
(TOBY) Disruptor effects a total ordering on consumption of incoming requests, so its IMPERATIVE to make sure you circuit-break anything which could possibly go unbounded (e.g. socket timeouts, disk access, etc)

# Implementation: Producers

* Used for Network IO, file system reads, etc
* Sequencing
* Protect against overwriting data in use
* Claim Strategy
* Batching effect (later)
* Circuit breakers

<img src="producer.png" class="illustration" note="final slash needed"/>

!SLIDE transition=fade
.notes Consumers that represent the same dependency share a ConsumerBarrier instance, but only one consumer per CB can have write access to any field in the entry
Consumers wait for a sequence to become available in the ring buffer before they read the entry using a WaitStrategy defined in the ConsumerBarrier; note that various strategies exist for waiting, and the choice depends on the priority of CPU resource versus latency and throughput
The sequential nature of the ring buffer allows you to introduce dependencies between processes that need something to happen before they move on (how you compose Consumers by ConsumerBarrier)
If CPU resource is more important, the consumer can wait on a condition variable protected by a lock that is signaled by a producer, which as mentioned before comes with a contention performance penalty
Consumers loop, checking the cursor representing the current available sequence in the ring buffer, which can be done with or without thread yield by trading CPU resource against latency - no lock or CAS to slow it down
Consumers merely provide a BatchHandler implementation that receives callbacks when data is available for consumption 
Read/Writes are minimized due to the performance cost of the volatile memory barrier

# Implementation: Consumers

* Composable by ConsumerBarrier
* Sequencing
* Wait Strategy
* BatchingHandler (later)

<img src="consumer.png" class="illustration" note="final slash needed"/>

!SLIDE transition=fade
.notes With a graph like (ie: diamond) model of producers and consumers (such as actors), queues are required to manage interactions between each of the elements
The single ring buffer replaces this in a single data structure for all of the elements, resulting in greatly reduced fixed costs of execution, increasing throughput and reducing latency
Care must be taken to ensure that state written by independent consumers doesn't result in the false sharing of cache lines

# Dependency Graphs

* One data structure for all consumers
* Increased throughput
* Reduced latency

<img src="consumer.png" class="illustration" note="final slash needed"/>

!SLIDE transition=fade
.notes This is the real win for latency
When a consumer falls behind due to latency, it has the ability to process all ring buffer elements up to the last committed by the producer, a capability not found in queues
Lagging consumers can therefore "catch up", increasing throughput and reducing/smoothing latency; near constant time for latency regardless of load, until memory subsystem is saturated, at which point the profile is linear following Little's Law
Producers also batch, and can write to the point in the ring buffer where the slowest consumer is currently working
Producers also have to manage a wait strategy when there are multiples of them; no "commits" to the ring buffer occur until the current sequence number is the one before the claimed slot
Performs better as load increases!  
Compared to "J" curve effect on latency observed with queues as load increases

# Batching Effect

* Catch-up capability
* Performs better as load increases
* "J" curve effect on latency with queues is gone

<img src="disruptor.png" class="illustration" note="final slash needed"/>

!SLIDE transition=fade
.notes Daily snapshot and restart to clear all memory
Replay events from a snapshot to see what happened when something goes awry

# Event Sourcing

* Daily snapshot
* Daily restart to clear all memory
* Replay events and errors

!SLIDE transition=fade

# When to use a Disruptor

* BALANCED FLOW

!SLIDE transition=fade

# SDisruptor

* My Scala port: http://github.com/jamie-allen/sdisruptor
	* Array-based
	* Order of execution matters
	* For comprehensions
	* Companion objects

!SLIDE transition=fade

# SDisruptor: Arrays

    class RingBuffer[T <: AbstractEntry : ClassManifest](entryFactory: EntryFactory[T], 
    								size: Int,
                    var claimStrategyOption: Symbol,
                    var waitStrategyOption: Symbol) extends ProducerBarrier[T] {
      val entries: Array[T] = new Array[T](sizeAsPowerOfTwo)

!SLIDE transition=fade

# SDisruptor: Order of Execution

    val p1, p2, p3, p4, p5, p6, p7: Long = -1L  // cache line padding
    @volatile private var _sequence: Long = RingBuffer.InitialCursorValue
    val p8, p9, p10, p11, p12, p13, p14: Long = -1L // cache line padding

!SLIDE transition=fade

# SDisruptor: For Comprehensions

  	for (i <- 0 until upperBounds.length) {
	    if (0L != counts(i)) {
        val upperBound = Math.min(upperBounds(i), maxValue)
        val midPoint = lowerBound + ((upperBound - lowerBound) / 2L)

        val intervalTotal = new BigDecimal(midPoint).multiply(new BigDecimal(counts(i)))
        total = total.add(intervalTotal)
	    }

	    lowerBound = Math.max(upperBounds(i) + 1L, minValue)
  	}

!SLIDE transition=fade

# SDisruptor: For Comprehensions

    // for (i <- counts.length - 1 until -1 by -1) {
    for (i <- counts.indices.reverse) { // indices.reverse is O(1), per Seth Tisue!
      if (0L != counts(i)) {
        tailCount += counts(i)
        if (tailCount >= tailTotal) return upperBounds(i)
      }
    }

!SLIDE transition=fade

# Links

* Blog: Processing 1M TPS with Axon Framework and the Disruptor: http://blog.jteam.nl/2011/07/20/processing-1m-tps-with-axon-framework-and-the-disruptor/
* QCon presentation: http://www.infoq.com/presentations/LMAX
* Google Group: http://groups.google.com/group/lmax-disruptor
* Martin Fowler's Bliki post: http://martinfowler.com/articles/lmax.html
* Martin Thompson's Mechanical Sympathy blog: http://mechanical-sympathy.blogspot.com/
* Trisha Gee's Mechanitis Blog: http://mechanitis.blogspot.com/
* Disruptor Wizard (simplifying dependency wiring): http://github.com/ajsutton/disruptorWizard
* The Demise of the Low Level Programmer: http://altdevblogaday.com/2011/08/06/demise-low-level-programmer/

Martin and his team will be presenting at JavaOne 2011