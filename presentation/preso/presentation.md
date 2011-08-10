!SLIDE title-page

## LMAX Disruptor

Jamie Allen

jallen@chariotsolutions.com

@jamie_allen

August 10, 2011

!SLIDE transition=fade

# There is nothing new here

* Why is the Disruptor pattern relevant?
* The "virtual" nature of our runtime and deployment environment has desensitized us as developers to the impact of our design and implementation decisions
* It flies in the face of popular concurrency abstractions to show what can be accomplished when implementing simple code in a highly-optimized fashion

!SLIDE transition=fade

# How Did They Arrive at the Disruptor

* Betfair attempts the Flywheel and 100x projects
* Looked into J2EE, SEDA, Actors, etc

!SLIDE transition=fade

# How Did They Arrive at the Disruptor

* Betfair begets Tradefair/LMAX
* LMAX implements with a clean slate

!SLIDE transition=fade

# Mechanical Sympathy

* Jackie Stewart - all great drivers must understand how their machine works to derive the fastest time driving it
* "The most amazing achievement of the computer software industry is its continuing cancellation of the steady and staggering gains made by the computer hardware industry." - Henry Peteroski, as quoted on Martin's blog

!SLIDE transition=fade

# Keys to the Disruptor's Performance

* Control the core
* Avoid lock arbitration 
* Minimize usage of memory barriers
* Pre-allocate and reuse memory
* Thread control

!SLIDE transition=fade

# Avoiding Locks

* Context switching is painful
* CAS semantics are much better, but no panacea
* Limited use of memory barriers are the key 

!SLIDE transition=fade

# What's Wrong With Queues

* Unbounded linked lists don't "stride"
* Bounded arrays share cache lines
* Cause heavy GC

!SLIDE transition=fade

# Memory Allocation

* Cache is king
* Pre-allocate and reuse
* Not functional programming
* Sequence data
* Cache lines and "false sharing"

!SLIDE transition=fade

# Caching

* registers
* store buffers
* L1 (SRAM)
* L2 (SRAM)
* L3 (SRAM)
* Main memory (DRAM)

!SLIDE transition=fade

# Cache Lines

* Cache "misses" are expensive
* Variables shouldn't share a cache line

!SLIDE transition=fade

# Striding

* Predictable access begets pre-fetching
* Data structures must be contiguous

!SLIDE transition=fade

# Garbage Collection

* Reuse of memory prevents GC and compaction
* Restart every day to clear the heap

!SLIDE transition=fade

# Implementation: Ring Buffer

* Bounded
* Pre-allocated all at once
* Re-traversed
* Bit mask modulus
* Your network card

!SLIDE transition=fade

# Implementation: Producers

* Used for Network IO, file system reads, etc
* No contention on sequence entry/allocation
* Protect against overwriting data in use
* Circuit breakers

!SLIDE transition=fade

# Implementation: Consumers

* Consumers wait for a sequence to become available in the ring buffer before they read the entry using a WaitStrategy defined in the ConsumerBarrier; note that various strategies exist for waiting, and the choice depends on the priority of CPU resource versus latency and throughput
* If CPU resource is more important, the consumer can wait on a condition variable protected by a lock that is signalled by a producer, which as mentioned before comes with a contention performance penalty
* Consumers loop, checking the cursor representing the current available sequence in the ring buffer, which can be done with or without thread yield by trading CPU resource against latency - no lock or CAS to slow it down
* Consumers that represent the same dependency share a ConsumerBarrier instance, but only one consumer per CB can have write access to any field in the entry

!SLIDE transition=fade

# Sequencing

* Basic counter for single producer, atomic int/long for multiple producers (using CAS to protect the counter)
* When a producer finishes copying data to a ring buffer element, it "commits" the transaction by updating a separate counter used by consumers to find out the next available data to use
* Consumers merely provide a BatchHandler implementation that receives callbacks when data is available for consumption 
* Consumers can be constructed into a graph of dependencies representing multiple stages in a processing pipeline
* Read/Writes are minimized due to the performance cost of the volatile memory barrier

!SLIDE transition=fade

# Batching Effect

* When a consumer falls behind due to latency, it has the ability to process all ring buffer elements up to the last committed by the producer, a capability not found in queues
* Lagging consumers can therefore "catch up", increasing throughput and reducing/smoothing latency; near constant time for latency regardless of load, until memory subsystem is saturated, at which point the profile is linear following Little's Law
* Producers also batch, and can write to the point in the ring buffer where the slowest consumer is currently working
* Producers also have to manage a wait strategy when there are multiples of them; no "commits" to the ring buffer occur until the current sequence number is the one before the claimed slot
* Compared to "J" curve effect on latency observed with queues as load increases

!SLIDE transition=fade

# Dependency Graphs

* With a graph like model of producers and consumers (such as actors), queues are required to manage interactions between each of the elements
* The single ring buffer replaces this in a single data structure for all of the elements, resulting in greatly reduced fixed costs of execution, increasing throughput and reducing latency
* Care must be taken to ensure that state written by independent consumers doesn't result in the false sharing of cache lines

!SLIDE transition=fade

# Event Sourcing

* Daily snapshot and restart to clear all memory
* Replay events from a snapshot to see what happened when something goes awry

!SLIDE transition=fade

# Use cases for Disruptor

* Note that the key is BALANCED FLOW - if your flow is unbalanced, you need to weigh the cost of losing local L3 cache with the reuse of cores

!SLIDE transition=fade

# Links

* Blog: Processing 1M TPS with Axon Framework and the Disruptor: http://blog.jteam.nl/2011/07/20/processing-1m-tps-with-axon-framework-and-the-disruptor/
* QCon presentation: http://www.infoq.com/presentations/LMAX
* Google Group: http://groups.google.com/group/lmax-disruptor
* Martin Fowler's Bliki post: http://martinfowler.com/articles/lmax.html
* Martin Thompson's Mechanical Sympathy blog: http://mechanical-sympathy.blogspot.com/
* Trisha Gee's Mechanitis Blog: http://mechanitis.blogspot.com/
* Disruptor Wizard (simplifying dependency wiring): http://github.com/ajsutton/disruptorWizard
* My Scala port: http://github.com/jamie-allen/sdisruptor

Presenting at JavaOne 2011