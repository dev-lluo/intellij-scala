package org.example2.usage

//
// SIMPLE
//
class Usage_Simple_1 {

  import org.example2.declaration.U

  val u: U = ???
}
class Usage_Simple_2 {

  import org.example2.declaration.{U, Y}

  val u: U = ???
  val y: Y = ???
}
class Usage_Simple_3 {

  import org.example2.declaration.{U, Y, Z}

  val u: U = ???
  val y: Y = ???
  val z: Z = ???
}

//
// SIMPLE SEPARATE IMPORTS
//
class Usage_Simple_SeparateImports_2 {

  import org.example2.declaration.U
  import org.example2.declaration.Y

  val u: U = ???
  val y: Y = ???
}

class Usage_Simple_SeparateImports_3 {

  import org.example2.declaration.U
  import org.example2.declaration.{Y, Z}

  val u: U = ???
  val y: Y = ???
  val z: Z = ???
}

//
// RENAMED
//
class Usage_Renamed_1 {

  import org.example2.declaration.{U => U_Renamed1}

  val u: U_Renamed1 = ???
}
class Usage_Renamed_2 {

  import org.example2.declaration.{Y, U => U_Renamed2}

  val u: U_Renamed2 = ???
  val y: Y = ???
}
class Usage_Renamed_3 {

  import org.example2.declaration.{Y, Z, U => U_Renamed3}

  val u: U_Renamed3 = ???
  val y: Y = ???
  val z: Z = ???
}

//
// RENAMED HIDDEN
//
class Usage_Renamed_Hidden_1 {

  import org.example2.declaration.{U => _}

}
class Usage_Renamed_Hidden_2 {

  import org.example2.declaration.{Y, U => _}

  val y: Y = ???
}
class Usage_Renamed_Hidden_3 {

  import org.example2.declaration.{Y, Z, U => _}

  val y: Y = ???
  val z: Z = ???
}

//
// RENAMED SEPARATE IMPORTS
//
class Usage_Renamed_SeparateImports_2 {

  import org.example2.declaration.Y
  import org.example2.declaration.{U => U_Renamed2}

  val u: U_Renamed2 = ???
  val y: Y = ???
}
class Usage_Renamed_SeparateImports_3 {

  import org.example2.declaration.{Y, Z}
  import org.example2.declaration.{U => U_Renamed3}

  val u: U_Renamed3 = ???
  val y: Y = ???
  val z: Z = ???
}

//
// RENAMED HIDDEN SEPARATE IMPORTS
//
class Usage_Renamed_Hidden_SeparateImports_2 {

  import org.example2.declaration.Y
  import org.example2.declaration.{U => _}

  val y: Y = ???
}
class Usage_Renamed_Hidden_SeparateImports_3 {

  import org.example2.declaration.{Y, Z}
  import org.example2.declaration.{U => _}

  val y: Y = ???
  val z: Z = ???
}