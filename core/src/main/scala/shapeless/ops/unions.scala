/*
 * Copyright (c) 2014 Miles Sabin 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package shapeless
package ops

object union {
  import shapeless.labelled.FieldType

  /**
   * Type class supporting union member selection.
   *
   * @author Miles Sabin
   */
  @annotation.implicitNotFound(msg = "No field ${K} in union ${C}")
  trait Selector[C <: Coproduct, K] {
    type V
    type Out = Option[V]
    def apply(l : C): Out
  }

  trait LowPrioritySelector {
    type Aux[C <: Coproduct, K, V0] = Selector[C, K] { type V = V0 }

    implicit def tlSelector[H, T <: Coproduct, K]
      (implicit st : Selector[T, K]): Aux[H :+: T, K, st.V] =
        new Selector[H :+: T, K] {
          type V = st.V
          def apply(u : H :+: T): Out = u match {
            case Inl(l) => None
            case Inr(r) => if(st == null) None else st(r)
          }
        }
  }

  object Selector extends LowPrioritySelector {
    def apply[C <: Coproduct, K](implicit selector: Selector[C, K]): Aux[C, K, selector.V] = selector

    implicit def hdSelector[K, V0, T <: Coproduct]: Aux[FieldType[K, V0] :+: T, K, V0] =
      new Selector[FieldType[K, V0] :+: T, K] {
        type V = V0
        def apply(u : FieldType[K, V] :+: T): Out = u match {
          case Inl(l) => Some(l)
          case Inr(r) => None
        }
      }
  }

  /**
   * Type class supporting collecting the keys of a union as an `HList`.
   * 
   * @author Miles Sabin
   */
  trait Keys[U <: Coproduct] extends DepFn0 { type Out <: HList }

  object Keys {
    def apply[U <: Coproduct](implicit keys: Keys[U]): Aux[U, keys.Out] = keys

    type Aux[U <: Coproduct, Out0 <: HList] = Keys[U] { type Out = Out0 }

    implicit def cnilKeys[U <: CNil]: Aux[U, HNil] =
      new Keys[U] {
        type Out = HNil
        def apply(): Out = HNil
      }

    implicit def coproductKeys[K, V, T <: Coproduct](implicit wk: Witness.Aux[K], kt: Keys[T]): Aux[FieldType[K, V] :+: T, K :: kt.Out] =
      new Keys[FieldType[K, V] :+: T] {
        type Out = K :: kt.Out
        def apply(): Out = wk.value :: kt()
      }
  }

  /**
   * Type class supporting collecting the value of a union as a `Coproduct`.
   * 
   * @author Miles Sabin
   */
  trait Values[U <: Coproduct] extends DepFn1[U] { type Out <: Coproduct }

  object Values {
    def apply[U <: Coproduct](implicit values: Values[U]): Aux[U, values.Out] = values

    type Aux[U <: Coproduct, Out0 <: Coproduct] = Values[U] { type Out = Out0 }

    implicit def cnilValues[U <: CNil]: Aux[U, CNil] =
      new Values[U] {
        type Out = CNil
        def apply(u: U): Out = u
      }

    implicit def coproductValues[K, V, T <: Coproduct](implicit vt: Values[T]): Aux[FieldType[K, V] :+: T, V :+: vt.Out] =
      new Values[FieldType[K, V] :+: T] {
        type Out = V :+: vt.Out
        def apply(l: FieldType[K, V] :+: T): Out = l match {
          case Inl(l) => Inl(l)
          case Inr(r) => Inr(vt(r))
        }
      }
  }

  /**
   * Type class supporting converting this union to a `Map` whose keys and values
   * are typed as the Lub of the keys and values of this union.
   *
   * @author Alexandre Archambault
   */
  trait ToMap[U <: Coproduct] extends DepFn1[U] {
    type Key
    type Value
    type Out = Map[Key, Value]
  }

  object ToMap {
    def apply[U <: Coproduct](implicit toMap: ToMap[U]): Aux[U, toMap.Key, toMap.Value] = toMap

    type Aux[U <: Coproduct, Key0, Value0] = ToMap[U] { type Key = Key0; type Value = Value0 }

    implicit def cnilToMap[K, V]: Aux[CNil, K, V] =
      new ToMap[CNil] {
        type Key = K
        type Value = V
        def apply(l: CNil) = Map.empty
      }

    implicit val cnilToMapAnyNothing: Aux[CNil, Any, Nothing] = cnilToMap[Any, Nothing]

    implicit def csingleToMap[K, V](implicit
      wk: Witness.Aux[K]
    ): Aux[FieldType[K, V] :+: CNil, K, V] =
      new ToMap[FieldType[K, V] :+: CNil] {
        type Key = K
        type Value = V
        def apply(c: FieldType[K, V] :+: CNil) = (c: @unchecked) match {
          case Inl(h) => Map(wk.value -> (h: V))
        }
      }

    implicit def coproductToMap[HK, HV, TH, TT <: Coproduct, TK, TV, K, V](implicit
      tailToMap: ToMap.Aux[TH :+: TT, TK, TV],
      keyLub: Lub[HK, TK, K],
      valueLub: Lub[HV, TV, V],
      wk: Witness.Aux[HK]
    ): Aux[FieldType[HK, HV] :+: TH :+: TT, K, V] =
      new ToMap[FieldType[HK, HV] :+: TH :+: TT] {
        type Key = K
        type Value = V
        def apply(c: FieldType[HK, HV] :+: TH :+: TT) = c match {
          case Inl(h) => Map(keyLub.left(wk.value) -> valueLub.left(h: HV))
          case Inr(t) => tailToMap(t).map{case (k, v) => keyLub.right(k) -> valueLub.right(v)}
        }
      }
  }
}
